/*
 * Copyright 2014, Mysema Ltd
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * http://www.apache.org/licenses/LICENSE-2.0
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.mysema.query.spatial;

import org.geolatte.geom.LineString;

import com.mysema.query.types.Expression;

/**
 * A LinearRing is a LineString that is both closed and simple.
 *
 * @author tiwe
 *
 * @param <T>
 */
public abstract class LinearRingExpression<T extends LineString> extends LineStringExpression<T> {

    private static final long serialVersionUID = -759466658721392938L;

    public LinearRingExpression(Expression<T> mixin) {
        super(mixin);
    }

}
