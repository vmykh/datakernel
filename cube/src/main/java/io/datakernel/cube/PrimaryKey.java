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

package io.datakernel.cube;

import com.google.common.collect.ImmutableList;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static java.lang.System.arraycopy;

public final class PrimaryKey implements Comparable<PrimaryKey> {
	private final Object[] values;

	private PrimaryKey(Object[] values) {
		this.values = values;
	}

	@SuppressWarnings("ToArrayCallWithZeroLengthArrayArgument")
	public static PrimaryKey ofList(List<Object> values) {
		return new PrimaryKey(values.toArray(new Object[]{}));
	}

	@SuppressWarnings("ToArrayCallWithZeroLengthArrayArgument")
	public static PrimaryKey ofObject(Object object, List<String> dimensions) {
		List<Object> values = new ArrayList<>();
		for (String dimension : dimensions) {
			try {
				Field field = object.getClass().getField(dimension);
				Object value = field.get(object);
				values.add(value);
			} catch (NoSuchFieldException | IllegalAccessException e) {
				throw new IllegalArgumentException(e);
			}
		}
		return ofList(values);
	}

	public static PrimaryKey ofArray(Object... values) {
		return new PrimaryKey(values);
	}

	public Object get(int index) {
		return values[index];
	}

	public PrimaryKey prefix(int size) {
		Object[] newValues = new Object[size];
		arraycopy(values, 0, newValues, 0, size);
		return PrimaryKey.ofArray(newValues);
	}

	public List<Object> values() {
		return ImmutableList.copyOf(values);
	}

	public int size() {
		return values.length;
	}

	public boolean isEmpty() {
		return size() == 0;
	}

	@SuppressWarnings({"EqualsWhichDoesntCheckParameterClass", "RedundantIfStatement"})
	@Override
	public boolean equals(Object o) {
		PrimaryKey that = (PrimaryKey) o;
		assert values.length == that.values.length;

		for (int i = 0; i < values.length; i++) {
			Object thisKey = values[i];
			Object thatKey = that.values[i];
			assert thisKey != null && thatKey != null;
			if (!thisKey.equals(thatKey))
				return false;
		}
		return true;
	}

	@Override
	public int hashCode() {
		return Arrays.hashCode(values);
	}

	@SuppressWarnings({"unchecked", "NullableProblems"})
	@Override
	public int compareTo(PrimaryKey o) {
		assert values.length == o.values.length;

		for (int i = 0; i < values.length; i++) {
			Object thisKey = values[i];
			Object thatKey = o.values[i];
			assert thisKey != null && thatKey != null;
			int result = ((Comparable<Object>) thisKey).compareTo(thatKey);
			if (result != 0)
				return result;
		}
		return 0;
	}

	@Override
	public String toString() {
		return Arrays.toString(values);
	}
}
