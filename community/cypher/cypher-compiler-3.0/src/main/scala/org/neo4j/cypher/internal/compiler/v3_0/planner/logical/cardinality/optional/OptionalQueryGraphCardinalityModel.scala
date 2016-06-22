/*
 * Copyright (c) 2002-2016 "Neo Technology,"
 * Network Engine for Objects in Lund AB [http://neotechnology.com]
 *
 * This file is part of Neo4j.
 *
 * Neo4j is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.neo4j.cypher.internal.compiler.v3_0.planner.logical.cardinality.optional

import org.neo4j.cypher.internal.compiler.v3_0.planner.QueryGraph
import org.neo4j.cypher.internal.compiler.v3_0.planner.logical.Cardinality
import org.neo4j.cypher.internal.compiler.v3_0.planner.logical.Metrics.{QueryGraphCardinalityModel, QueryGraphSolverInput}
import org.neo4j.cypher.internal.frontend.v3_0.SemanticTable

case class OptionalQueryGraphCardinalityModel(inner: QueryGraphCardinalityModel) extends QueryGraphCardinalityModel {

  override def apply(qg: QueryGraph, input: QueryGraphSolverInput, semanticTable: SemanticTable): Cardinality = {
    val combinations: Seq[QueryGraph] = findQueryGraphCombinations(qg)
    val cardinalities: Seq[Cardinality] = combinations.map(inner(_, input, semanticTable))
    cardinalities.max
  }

  private def findQueryGraphCombinations(queryGraph: QueryGraph): Seq[QueryGraph] = {
    (0 to queryGraph.optionalMatches.length).flatMap(queryGraph.optionalMatches.combinations)
      .map(_.map(_.withoutArguments()))
      .map(_.foldLeft(QueryGraph.empty)(_.withOptionalMatches(Vector.empty) ++ _.withOptionalMatches(Vector.empty)))
      .map(queryGraph.withOptionalMatches(Vector.empty) ++ _)
  }
}
