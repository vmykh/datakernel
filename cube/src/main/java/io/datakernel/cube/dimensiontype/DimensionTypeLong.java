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
import io.datakernel.serializer.asm.SerializerGenLong;

public class DimensionTypeLong extends DimensionType implements DimensionTypeEnumerable {
	public DimensionTypeLong() {
		super(long.class);
	}

	@Override
	public SerializerGen serializerGen() {
		return new SerializerGenLong(true);
	}

	@Override
	public Object toInternalRepresentation(String o) {
		return Long.valueOf(o);
	}

	@Override
	public Object increment(Object object) {
		Long longToIncrement = (Long) object;
		return ++longToIncrement;
	}

	@Override
	public long difference(Object o1, Object o2) {
		return ((Long) o1) - ((Long) o2);
	}

	@Override
	public int compare(Object o1, Object o2) {
		return ((Long) o1).compareTo((Long) o2);
	}
}
