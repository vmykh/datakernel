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

package io.datakernel.serializer.annotations;

import com.google.common.base.Throwables;
import io.datakernel.serializer.SerializerScanner;
import io.datakernel.serializer.asm.SerializerGen;
import io.datakernel.serializer.asm.SerializerGenBuilder;
import io.datakernel.serializer.asm.SerializerGenBuilderConst;

public final class SerializerClassHandler implements AnnotationHandler<SerializerClass, SerializerClassEx> {
	@Override
	public SerializerGenBuilder createBuilder(SerializerScanner serializerScanner, final SerializerClass annotation) {
		try {
			SerializerGen serializer = annotation.value().newInstance();
			return new SerializerGenBuilderConst(serializer);
		} catch (InstantiationException | IllegalAccessException e) {
			throw Throwables.propagate(e);
		}
	}

	@Override
	public int[] extractPath(SerializerClass annotation) {
		return annotation.path();
	}

	@Override
	public SerializerClass[] extractList(SerializerClassEx plural) {
		return plural.value();
	}
}
