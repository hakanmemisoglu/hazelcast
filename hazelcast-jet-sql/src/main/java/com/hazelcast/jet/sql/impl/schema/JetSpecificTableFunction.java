/*
 * Copyright (c) 2008-2021, Hazelcast, Inc. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.hazelcast.jet.sql.impl.schema;

import com.hazelcast.jet.sql.impl.connector.SqlConnector;
import com.hazelcast.sql.impl.calcite.schema.HazelcastTable;
import com.hazelcast.sql.impl.calcite.validate.operators.common.HazelcastFunction;
import com.hazelcast.sql.impl.expression.Expression;
import org.apache.calcite.sql.SqlFunctionCategory;
import org.apache.calcite.sql.SqlKind;
import org.apache.calcite.sql.type.SqlOperandTypeInference;
import org.apache.calcite.sql.type.SqlReturnTypeInference;

import java.util.List;

/**
 * A table function return type of which is known upfront.
 */
public abstract class JetSpecificTableFunction extends HazelcastFunction implements JetTableFunction {

    private final SqlConnector connector;

    protected JetSpecificTableFunction(
            String name,
            SqlReturnTypeInference returnTypeInference,
            SqlOperandTypeInference operandTypeInference,
            SqlConnector connector
    ) {
        super(
                name,
                SqlKind.OTHER_FUNCTION,
                returnTypeInference,
                operandTypeInference,
                SqlFunctionCategory.USER_DEFINED_TABLE_SPECIFIC_FUNCTION
        );

        this.connector = connector;
    }

    @Override
    public boolean isStream() {
        return connector.isStream();
    }

    public abstract HazelcastTable toTable(List<Expression<?>> argumentExpressions);
}
