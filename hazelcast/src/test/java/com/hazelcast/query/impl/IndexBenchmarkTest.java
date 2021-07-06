package com.hazelcast.query.impl;

import com.hazelcast.config.Config;
import com.hazelcast.config.IndexConfig;
import com.hazelcast.config.IndexType;
import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.map.IMap;
import com.hazelcast.test.HazelcastSerialClassRunner;
import com.hazelcast.test.HazelcastTestSupport;
import com.hazelcast.test.TestHazelcastInstanceFactory;
import com.hazelcast.test.annotation.Repeat;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.Serializable;
import java.util.Objects;

@RunWith(HazelcastSerialClassRunner.class)
public class IndexBenchmarkTest extends HazelcastTestSupport {
    private static final String mapName = "map1";
    private static final String orderedIndexName = "index_age_sorted";
    private static final String compositeOrderedIndexName = "index_age_name_sorted";
    private static final String hashIndexName = "index_age_hash";

    private HazelcastInstance instance;
    private Config config;

    private IMap<String, Person> map;

    @Before
    public void setup() {
        TestHazelcastInstanceFactory factory = createHazelcastInstanceFactory(1);

        config = smallInstanceConfig();
        instance = factory.newHazelcastInstance(config);

        map = instance.getMap(mapName);

        map.addIndex(new IndexConfig(IndexType.SORTED, "age").setName(orderedIndexName));
    }

    @Test
    @Repeat(10)
    public void benchmark1() {
        double begin = getMemoryUsageGB();
        insertIntoMap(map, 1000000, 1000);
        double end = getMemoryUsageGB();

        System.out.println("BEGINNING MEMORY USAGE: " + begin + " MB");
        System.out.println("END MEMORY USAGE: " + end + " MB");
    }

    @Test
    @Repeat(10)
    public void benchmark2() {
        double begin = getMemoryUsageGB();
        insertIntoMap(map, 1000000, 1);
        double end = getMemoryUsageGB();

        System.out.println("BEGINNING MEMORY USAGE: " + begin + "MB");
        System.out.println("END MEMORY USAGE: " + end + "MB");
    }

    private static void insertIntoMap(IMap<String, Person> map, int numObject, int elementsPerIndexKey) {
        int rangeHigh = numObject / elementsPerIndexKey;
        for (int i = 0; i < rangeHigh; i++) {
            for (int j = 0; j < elementsPerIndexKey; j++) {
                int num = i * elementsPerIndexKey + j;
                map.put("Name: " + num, new Person("Name: " + num, i, "Dept"));
            }
        }
    }

    private static double getMemoryUsageGB() {
        long mem = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory();
        return ((double) mem) / (1 << 20);
    }

    static class Person implements Serializable {
        private String name;
        private Integer age;
        private String department;

        Person(String name, Integer age, String department) {
            this.name = name;
            this.age = age;
            this.department = department;
        }

        public String getName() {
            return this.name;
        }

        public Integer getAge() {
            return this.age;
        }

        public String getDepartment() {
            return this.department;
        }

        public void setName(String name) {
            this.name = name;
        }

        public void setAge(Integer age) {
            this.age = age;
        }

        public void setDepartment(String department) {
            this.department = department;
        }

        @Override
        public String toString() {
            return "Person{" + "name='" + name + '\''
                    + ", age=" + age + ", department='"
                    + department + '\'' + '}';
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            Person person = (Person) o;
            return Objects.equals(name, person.name)
                    && Objects.equals(age, person.age)
                    && Objects.equals(department, person.department);
        }

        @Override
        public int hashCode() {
            return Objects.hash(name, age, department);
        }
    }
}
