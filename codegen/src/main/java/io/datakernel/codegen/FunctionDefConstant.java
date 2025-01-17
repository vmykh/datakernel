/*
 * Copyright (C) 2015 SoftIndex LLC.
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

package io.datakernel.codegen;

import com.google.common.primitives.Primitives;
import org.objectweb.asm.Type;
import org.objectweb.asm.commons.GeneratorAdapter;

import static com.google.common.base.Preconditions.checkNotNull;
import static org.objectweb.asm.Type.getType;

/**
 * Defines methods to create a constant value
 */
public final class FunctionDefConstant implements FunctionDef {

	private final Object value;

	FunctionDefConstant(Object value) {
		checkNotNull(value);
		this.value = value;
	}

	public Object getValue() {
		return value;
	}

	@Override
	public Type type(Context ctx) {
		return getType(Primitives.unwrap(value.getClass()));
	}

	@Override
	public Type load(Context ctx) {
		GeneratorAdapter g = ctx.getGeneratorAdapter();
		Type type = type(ctx);
		if (value instanceof Byte) {
			g.push((Byte) value);
		} else if (value instanceof Short) {
			g.push((Short) value);
		} else if (value instanceof Integer) {
			g.push((Integer) value);
		} else if (value instanceof Long) {
			g.push((Long) value);
		} else if (value instanceof Float) {
			g.push((Float) value);
		} else if (value instanceof Double) {
			g.push((Double) value);
		} else if (value instanceof Boolean) {
			g.push((Boolean) value);
		} else if (value instanceof Character) {
			g.push((Character) value);
		} else if (value instanceof String) {
//            if (argumentDataType instanceof DataTypeEnum) {
//                DataTypeEnum dataTypeEnum = (DataTypeEnum) argumentDataType;
//                Integer ordinal = dataTypeEnum.getValuesBiMap().inverse().get(value);
//                checkNotNull(ordinal);
//                g.push((byte) (int) ordinal);
//            } else
			g.push((String) value);
		} else
			throw new IllegalArgumentException();
		return type;
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) return true;
		if (o == null || getClass() != o.getClass()) return false;

		FunctionDefConstant that = (FunctionDefConstant) o;

		if (value != null ? !value.equals(that.value) : that.value != null) return false;

		return true;
	}

	@Override
	public int hashCode() {
		return value != null ? value.hashCode() : 0;
	}
}
