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

package io.datakernel.cube.dimensiontype;

import io.datakernel.serializer.asm.SerializerGen;
import io.datakernel.serializer.asm.SerializerGenString;

public class DimensionTypeString extends DimensionType {
	public DimensionTypeString() {
		super(String.class);
	}

	@Override
	public SerializerGen serializerGen() {
		return new SerializerGenString();
	}

	@Override
	public Object toInternalRepresentation(String o) {
		return o;
	}

	@Override
	public int compare(Object o1, Object o2) {
		return ((String) o1).compareTo((String) o2);
	}
}
