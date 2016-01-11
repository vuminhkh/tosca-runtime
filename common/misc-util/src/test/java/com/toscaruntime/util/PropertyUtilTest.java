package com.toscaruntime.util;

import java.util.List;
import java.util.Map;

import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;

/**
 * @author Minh Khang VU
 */
@RunWith(JUnit4.class)
public class PropertyUtilTest {

    @Test
    public void test() {
        Map<String, Object> test = Maps.newHashMap();
        test.put("toto", "tata");
        test.put("titi", Maps.newHashMap());
        test.put("fcuk", Lists.newArrayList("xx", "yy"));
        test.put("fcok", Lists.newArrayList("zz", "tt").toArray());
        Map<String, Object> map1 = Maps.newHashMap();
        map1.put("k1", "v1");
        Map<String, Object> map2 = Maps.newHashMap();
        map2.put("k2", "v2");
        List<String> list3 = Lists.newArrayList("1", "2", "3");
        test.put("realComplex", Lists.newArrayList(map1, map2, list3));
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
