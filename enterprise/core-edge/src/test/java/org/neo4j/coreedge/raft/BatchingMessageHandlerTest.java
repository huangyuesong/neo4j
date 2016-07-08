/*
 * Copyright (c) 2002-2016 "Neo Technology,"
 * Network Engine for Objects in Lund AB [http://neotechnology.com]
 *
 * This file is part of Neo4j.
 *
 * Neo4j is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package org.neo4j.coreedge.raft;

import org.junit.Before;
import org.junit.Test;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.neo4j.coreedge.catchup.storecopy.LocalDatabase;
import org.neo4j.coreedge.raft.net.Inbound;
import org.neo4j.coreedge.server.StoreId;
import org.neo4j.logging.NullLogProvider;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyZeroInteractions;
import static org.mockito.Mockito.when;

public class BatchingMessageHandlerTest
{
    private static final int MAX_BATCH = 16;
    private static final int QUEUE_SIZE = 64;
    private LocalDatabase localDatabase = mock( LocalDatabase.class );
    private RaftStateMachine raftStateMachine = mock( RaftStateMachine.class );

    @Before
    public void setup()
    {
        when(localDatabase.storeId()).thenReturn( new StoreId( 1,2,3,4 ) );
    }

    @Test
    public void shouldInvokeInnerHandlerWhenRun() throws Exception
    {
        // given
        @SuppressWarnings( "unchecked" )
        Inbound.MessageHandler<RaftMessages.RaftMessage> innerHandler = mock( Inbound.MessageHandler.class );

        BatchingMessageHandler batchHandler = new BatchingMessageHandler(
                innerHandler, NullLogProvider.getInstance(), QUEUE_SIZE, MAX_BATCH, localDatabase, raftStateMachine );
        RaftMessages.NewEntry.Request message = new RaftMessages.NewEntry.Request( null, null );

        batchHandler.handle(  new RaftMessages.StoreIdAwareMessage( new StoreId( 1,2,3,4 ), message )  );
        verifyZeroInteractions( innerHandler );

        // when
        batchHandler.run();

        // then
        verify( innerHandler ).handle( message );
    }

    @Test
    public void shouldInvokeHandlerOnQueuedMessage() throws Exception
    {
        // given
        @SuppressWarnings( "unchecked" )
        Inbound.MessageHandler<RaftMessages.RaftMessage> innerHandler = mock( Inbound.MessageHandler.class );

        BatchingMessageHandler batchHandler = new BatchingMessageHandler(
                innerHandler, NullLogProvider.getInstance(), QUEUE_SIZE, MAX_BATCH, localDatabase, raftStateMachine );
        RaftMessages.NewEntry.Request message = new RaftMessages.NewEntry.Request( null, null );

        ExecutorService executor = Executors.newCachedThreadPool();
        Future<?> future = executor.submit( batchHandler );

        // Some time for letting the batch handler block on its internal queue.
        //
        // It is fine if it sometimes doesn't get that far in time, just that we
        // usually want to test the wake up from blocking state.
        Thread.sleep( 50 );

        // when
        batchHandler.handle(  new RaftMessages.StoreIdAwareMessage( new StoreId( 1,2,3,4 ), message )  );

        // then
        future.get();
        verify( innerHandler ).handle( message );
    }

    @Test
    public void shouldBatchRequests() throws Exception
    {
        // given
        @SuppressWarnings( "unchecked" )
        Inbound.MessageHandler<RaftMessages.RaftMessage> innerHandler = mock( Inbound.MessageHandler.class );

        BatchingMessageHandler batchHandler = new BatchingMessageHandler(
                innerHandler, NullLogProvider.getInstance(), QUEUE_SIZE, MAX_BATCH, localDatabase, raftStateMachine );
        ReplicatedString contentA = new ReplicatedString( "A" );
        ReplicatedString contentB = new ReplicatedString( "B" );
        RaftMessages.NewEntry.Request messageA = new RaftMessages.NewEntry.Request( null, contentA );
        RaftMessages.NewEntry.Request messageB = new RaftMessages.NewEntry.Request( null, contentB );

        batchHandler.handle(  new RaftMessages.StoreIdAwareMessage( new StoreId( 1,2,3,4 ), messageA )  );
        batchHandler.handle(  new RaftMessages.StoreIdAwareMessage( new StoreId( 1,2,3,4 ), messageB )  );
        verifyZeroInteractions( innerHandler );

        // when
        batchHandler.run();

        // then
        RaftMessages.NewEntry.Batch batch = new RaftMessages.NewEntry.Batch( 2 );
        batch.add( contentA );
        batch.add( contentB );
        verify( innerHandler ).handle( batch );
    }

    @Test
    public void shouldBatchNewEntriesAndHandleOtherMessagesSingularly() throws Exception
    {
        // given
        @SuppressWarnings( "unchecked" )
        Inbound.MessageHandler<RaftMessages.RaftMessage> innerHandler = mock( Inbound.MessageHandler.class );

        BatchingMessageHandler batchHandler = new BatchingMessageHandler(
                innerHandler, NullLogProvider.getInstance(), QUEUE_SIZE, MAX_BATCH, localDatabase, raftStateMachine );
        ReplicatedString contentA = new ReplicatedString( "A" );
        ReplicatedString contentC = new ReplicatedString( "C" );

        RaftMessages.NewEntry.Request messageA = new RaftMessages.NewEntry.Request( null, contentA );
        RaftMessages.Heartbeat messageB = new RaftMessages.Heartbeat( null, 0, 0, 0 );
        RaftMessages.NewEntry.Request messageC = new RaftMessages.NewEntry.Request( null, contentC );
        RaftMessages.Heartbeat messageD = new RaftMessages.Heartbeat( null, 1, 1, 1 );

        batchHandler.handle( new RaftMessages.StoreIdAwareMessage( new StoreId( 1,2,3,4 ), messageA ) );
        batchHandler.handle( new RaftMessages.StoreIdAwareMessage( new StoreId( 1,2,3,4 ), messageB ) );
        batchHandler.handle( new RaftMessages.StoreIdAwareMessage( new StoreId( 1,2,3,4 ), messageC ) );
        batchHandler.handle( new RaftMessages.StoreIdAwareMessage( new StoreId( 1,2,3,4 ), messageD ) );
        verifyZeroInteractions( innerHandler );

        // when
        batchHandler.run();

        // then
        RaftMessages.NewEntry.Batch batch = new RaftMessages.NewEntry.Batch( 2 );
        batch.add( contentA );
        batch.add( contentC );

        verify( innerHandler ).handle( batch );
        verify( innerHandler ).handle( messageB );
        verify( innerHandler ).handle( messageD );
    }
}
