/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.hudi.bifunction.wrapper;

import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Stream;

public class BiFunctionWrapper {
  public static <T> Consumer<T> throwingConsumerWrapper(ThrowingConsumer<T, Exception> throwingConsumer) {
    Objects.requireNonNull(throwingConsumer);
    return i -> {
      try {
        throwingConsumer.accept(i);
      } catch (Exception ex) {
        throw new RuntimeException(ex);
      }
    };
  }

  public static <T, R> Function<T, R> throwingMapWrapper(ThrowingFunction<T, R, Exception> throwingMap) {
    Objects.requireNonNull(throwingMap);
    return i -> {
      try {
        return throwingMap.apply(i);
      } catch (Exception e) {
        throw new RuntimeException(e);
      }
    };
  }

  public static <T, R> Function<T, Stream<? extends R>> throwingFlatMapWrapper(ThrowingFunction<? super T, ? extends Stream<? extends R>, Exception> throwingFlatMap) {
    Objects.requireNonNull(throwingFlatMap);
    return i -> {
      try {
        return throwingFlatMap.apply(i);
      } catch (Exception e) {
        throw new RuntimeException(e);
      }
    };
  }
}
