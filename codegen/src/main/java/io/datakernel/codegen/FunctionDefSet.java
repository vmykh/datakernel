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

import org.objectweb.asm.Type;

/**
 * Defines methods which allow to set fields
 */
public final class FunctionDefSet implements FunctionDef {
	private final StoreDef to;
	private final FunctionDef from;

	public FunctionDefSet(StoreDef to, FunctionDef from) {
		this.to = to;
		this.from = from;
	}

	@Override
	public Type type(Context ctx) {
		return Type.VOID_TYPE;
	}

	@Override
	public Type load(Context ctx) {
		Object storeContext = to.beginStore(ctx);
		Type type = from.load(ctx);
		to.store(ctx, storeContext, type);
		return Type.VOID_TYPE;
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) return true;
		if (o == null || getClass() != o.getClass()) return false;

		FunctionDefSet that = (FunctionDefSet) o;

		return (from.equals(that.from)) && (to.equals(that.to));
	}

	@Override
	public int hashCode() {
		int result = to.hashCode();
		result = 31 * result + from.hashCode();
		return result;
	}
}
