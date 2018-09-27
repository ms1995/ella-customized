package com.apposcopy.ella.runtime;

import java.lang.reflect.Method;

public abstract class EllaWrapper {

    private static Method m_method;

    static {
        try {
            Class<?> ella_class = Class.forName("com.apposcopy.ella.runtime.Ella");
            m_method = ella_class.getDeclaredMethod("m", int.class);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public static void m(int mId) {
        try {
            m_method.invoke(null, mId);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

}
