package com.mysema.query.suites;

import org.junit.BeforeClass;

import com.mysema.query.JPABase;
import com.mysema.query.JPASQLBase;
import com.mysema.query.Mode;
import com.mysema.query.Target;
import com.mysema.query.jpa.JPAIntegrationBase;
import com.mysema.query.jpa.SerializationBase;

public class DerbyEclipseLinkTest extends AbstractJPASuite {

    public static class JPA extends JPABase {
        @Override
        public void Order_StringValue() {
            // not supported in MySQL/EclipseLink
        }
        @Override
        public void Order_StringValue_To_Integer() {
            // not supported in MySQL/EclipseLink
        }
        @Override
        public void Order_StringValue_ToLong() {
            // not supported in MySQL/EclipseLink
        }
        @Override
        public void Order_StringValue_ToBigInteger() {
            // not supported in MySQL/EclipseLink
        }
    }
    public static class JPASQL extends JPASQLBase {}
    public static class JPAIntegration extends JPAIntegrationBase {}
    public static class Serialization extends SerializationBase {}

    @BeforeClass
    public static void setUp() throws Exception {
        Mode.mode.set("derby-eclipselink");
        Mode.target.set(Target.DERBY);
    }
}
