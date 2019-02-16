/*
 * Copyright 2019. Santanu Sinha
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in
 * compliance with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is
 * distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either
 * express or implied. See the License for the specific language governing permissions and limitations
 * under the License.
 */

package io.appform.hope.core.values;

import io.appform.hope.core.Value;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

/**
 * Abstraction of a value that is generated as eval of a {@link io.appform.hope.core.functions.HopeFunction} call.
 */
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public abstract class FunctionEvaluatableValue extends Value {
    protected final FunctionValue function;

    protected FunctionEvaluatableValue() {
        this(null);
    }

    protected FunctionEvaluatableValue(FunctionValue function) {
        this.function = function;
    }
}
