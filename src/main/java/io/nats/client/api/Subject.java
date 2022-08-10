// Copyright 2022 The NATS Authors
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at:
//
// http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package io.nats.client.api;

import io.nats.client.support.JsonUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

public class Subject implements Comparable<Subject> {
    private final String name;
    private final long count;

    static List<Subject> optionalListOf(String json) {
        List<Subject> list = new ArrayList<>();
        if (json != null) {
            Map<String, Long> map = JsonUtils.getMapOfLongs(json);
            for (String subject : map.keySet()) {
                list.add(new Subject(subject, map.get(subject)));
            }
            Collections.sort(list);
        }
        return list.isEmpty() ? null : list;
    }

    private Subject(String name, long count) {
        this.name = name;
        this.count = count;
    }

    /**
     * Get the subject name
     * @return the subject
     */
    public String getName() {
        return name;
    }

    /**
     * Get the subject message count
     * @return the count
     */
    public long getCount() {
        return count;
    }

    @Override
    public String toString() {
        return "Subject{" +
            "name='" + name + '\'' +
            ", count=" + count +
            '}';
    }

    @Override
    public int compareTo(Subject o) {
        return name.compareTo(o.name);
    }
}