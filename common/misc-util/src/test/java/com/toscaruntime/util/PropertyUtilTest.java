package com.toscaruntime.util;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;


/**
 * @author Minh Khang VU
 */
@RunWith(JUnit4.class)
public class PropertyUtilTest {

    @Test
    public void test() {
        Map<String, Object> test = new HashMap<>();
        test.put("toto", "tata");
        test.put("titi", new HashMap<String, Object>());
        test.put("fcuk", new ArrayList<>(Arrays.asList("xx", "yy")));
        test.put("fcok", new String[]{"zz", "tt"});
        Map<String, Object> map1 = new HashMap<>();
        map1.put("k1", "v1");
        Map<String, Object> map2 = new HashMap<>();
        map2.put("k2", "v2");
        List<String> list3 = new ArrayList<>(Arrays.asList("1", "2", "3"));
        test.put("realComplex", new ArrayList<>(Arrays.asList(map1, map2, list3)));
        ((Map<String, Object>) test.get("titi")).put("toctoc", "tactac");
        Assert.assertEquals("tactac", PropertyUtil.getProperty(test, "titi.toctoc"));
        Assert.assertEquals("yy", PropertyUtil.getProperty(test, "fcuk[1]"));
        Assert.assertEquals("zz", PropertyUtil.getProperty(test, "fcok.0"));
        Assert.assertEquals("v1", PropertyUtil.getProperty(test, "realComplex[0].k1"));
        Assert.assertEquals("1", PropertyUtil.getProperty(test, "realComplex[2][0]"));
        Assert.assertNull(PropertyUtil.getProperty(test, "path.not.exist"));
        Assert.assertNull(PropertyUtil.getProperty(test, "fcuk.fcuk"));
    }
}
