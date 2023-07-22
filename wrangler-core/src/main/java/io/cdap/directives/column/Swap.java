/*
 *  Copyright © 2017-2019 Cask Data, Inc.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License"); you may not
 *  use this file except in compliance with the License. You may obtain a copy of
 *  the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 *  WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 *  License for the specific language governing permissions and limitations under
 *  the License.
 */

package io.cdap.directives.column;

import io.cdap.cdap.api.annotation.Description;
import io.cdap.cdap.api.annotation.Name;
import io.cdap.cdap.api.annotation.Plugin;
import io.cdap.cdap.etl.api.relational.ExpressionFactory;
import io.cdap.cdap.etl.api.relational.InvalidRelation;
import io.cdap.cdap.etl.api.relational.Relation;
import io.cdap.cdap.etl.api.relational.RelationalTranformContext;
import io.cdap.wrangler.api.Arguments;
import io.cdap.wrangler.api.Directive;
import io.cdap.wrangler.api.DirectiveExecutionException;
import io.cdap.wrangler.api.DirectiveParseException;
import io.cdap.wrangler.api.ExecutorContext;
import io.cdap.wrangler.api.Row;
import io.cdap.wrangler.api.annotations.Categories;
import io.cdap.wrangler.api.lineage.Lineage;
import io.cdap.wrangler.api.lineage.Many;
import io.cdap.wrangler.api.lineage.Mutation;
import io.cdap.wrangler.api.parser.ColumnName;
import io.cdap.wrangler.api.parser.TokenType;
import io.cdap.wrangler.api.parser.UsageDefinition;
import io.cdap.wrangler.utils.SqlExpressionGenerator;

import java.util.List;
import java.util.Optional;

/**
 * A directive for swapping the column names.
 */
@Plugin(type = Directive.TYPE)
@Name(Swap.NAME)
@Categories(categories = { "column"})
@Description("Swaps the column names of two columns.")
public class Swap implements Directive, Lineage {
  public static final String NAME = "swap";
  private String left;
  private String right;

  @Override
  public UsageDefinition define() {
    UsageDefinition.Builder builder = UsageDefinition.builder(NAME);
    builder.define("left", TokenType.COLUMN_NAME);
    builder.define("right", TokenType.COLUMN_NAME);
    return builder.build();
  }

  @Override
  public void initialize(Arguments args) throws DirectiveParseException {
    left = ((ColumnName) args.value("left")).value();
    right = ((ColumnName) args.value("right")).value();
  }

  @Override
  public void destroy() {
    // no-op
  }

  @Override
  public List<Row> execute(List<Row> rows, ExecutorContext context) throws DirectiveExecutionException {
    for (Row row : rows) {
      int sidx = row.find(left);
      int didx = row.find(right);

      if (sidx == -1) {
        throw new DirectiveExecutionException(NAME, String.format("Column '%s' does not exist.", left));
      }

      if (didx == -1) {
        throw new DirectiveExecutionException(NAME, String.format("Column '%s' does not exist.", right));
      }

      row.setColumn(sidx, right);
      row.setColumn(didx, left);
    }
    return rows;
  }

  @Override
  public Mutation lineage() {
    return Mutation.builder()
      .readable("Swapped columns '%s' and '%s'", left, right)
      .relation(Many.of(left, right), Many.of(right, left))
      .build();
  }

  @Override
  public Relation transform(RelationalTranformContext relationalTranformContext,
                            Relation relation) {

    Optional<ExpressionFactory<String>> expressionFactory = SqlExpressionGenerator
            .getExpressionFactory(relationalTranformContext);
    if (!expressionFactory.isPresent()) {
      return new InvalidRelation("Cannot find an Expression Factory");
    }

    Relation tempRel =  relation.setColumn("tempColumn", expressionFactory.get().compile(right));
    tempRel = tempRel.setColumn(right, expressionFactory.get().compile(left));
    return tempRel.setColumn(left, expressionFactory.get().compile("tempColumn"));
  }
}
