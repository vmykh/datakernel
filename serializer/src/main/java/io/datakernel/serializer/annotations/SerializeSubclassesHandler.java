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

import io.datakernel.serializer.SerializerScanner;
import io.datakernel.serializer.asm.SerializerGen;
import io.datakernel.serializer.asm.SerializerGenBuilder;

import static com.google.common.base.Preconditions.checkArgument;

public final class SerializeSubclassesHandler implements AnnotationHandler<SerializeSubclasses, SerializeSubclassesEx> {
	@Override
	public SerializerGenBuilder createBuilder(final SerializerScanner serializerScanner, final SerializeSubclasses annotation) {
		return new SerializerGenBuilder() {
			@Override
			public SerializerGen serializer(Class<?> superclass, SerializerForType[] superclassGenerics, SerializerGen fallback) {
				checkArgument(superclass.getTypeParameters().length == 0);
				checkArgument(superclassGenerics.length == 0);

				return serializerScanner.createSubclassesSerializer(superclass, annotation);
			}
		};
	}

	@Override
	public int[] extractPath(SerializeSubclasses annotation) {
		return annotation.path();
	}

	@Override
	public SerializeSubclasses[] extractList(SerializeSubclassesEx plural) {
		return plural.value();
	}
}
