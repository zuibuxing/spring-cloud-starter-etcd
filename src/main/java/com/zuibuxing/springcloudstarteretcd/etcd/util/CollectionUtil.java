package com.zuibuxing.springcloudstarteretcd.etcd.util;

import java.util.*;

public class CollectionUtil {

    private CollectionUtil() {
        super();
    }

    // 判断一个集合是否为空
    public static <T> boolean isEmpty(Collection<T> col) {
        if (col == null || col.isEmpty()) {
            return true;
        }

        return false;
    }

    // 判断一个集合是否不为空
    public static <T> boolean isNotEmpty(Collection<T> col) {
        return !isEmpty(col);
    }

    // 判断Map是否为空
    public static <K, V> boolean isEmpty(Map<K, V> map) {
        if (map == null || map.isEmpty()) {
            return true;
        }

        return false;
    }

    // 判断Map是否不为空为空
    public static <K, V> boolean isNotEmpty(Map<K, V> map) {
        return !isEmpty(map);
    }

    // 去除list中的重复数据
    public static <T> List<T> removeRepeat(List<T> list) {
        if (isEmpty(list)) {
            return list;
        }

        List<T> result = new ArrayList<T>();
        for (T e : list) {
            if (!result.contains(e)) {
                result.add(e);
            }
        }

        return result;
    }

    // 将集合转换为String数组
    public static <T> String[] toArray(List<T> list) {
        if (isEmpty(list)) {
            return null;
        }

        String[] result = new String[list.size()];
        for (int i = 0; i < list.size(); i++) {
            result[i] = String.valueOf(list.get(i));
        }

        return result;
    }

    /**
     * 判断对象为空
     *
     * @param obj
     *            对象名
     * @return 是否为空
     */
    @SuppressWarnings("rawtypes")
    public static boolean isEmpty(Object obj)
    {
        if (obj == null)
        {
            return true;
        }

        if ((obj instanceof List))
        {
            return ((List) obj).size() == 0;
        }

        if ((obj instanceof Map))
        {
            return ((Map) obj).size() == 0;
        }

        if ((obj instanceof Set))
        {
            return ((Set) obj).size() == 0;
        }

        if ((obj instanceof String))
        {
            return ((String) obj).trim().equals("");
        }
        return false;
    }

    /**
     * 判断对象不为空
     *
     * @param obj
     *            对象名
     * @return 是否不为空
     */
    public static boolean isNotEmpty(Object obj)
    {
        return !isEmpty(obj);
    }

}
