package edu.unsw.core.config.jackson;

import tools.jackson.databind.SerializationContext;
import tools.jackson.core.JsonGenerator;
import tools.jackson.databind.annotation.JacksonStdImpl;
import tools.jackson.databind.ser.jdk.NumberSerializer;

/***
 * @author 徐一杰 YijieXu Bubble
 * @date 2026/6/17 18:36
 * @description When a numeric type exceeds the range specified in JavaScript, it is converted to a String
 */
@JacksonStdImpl
public class BigNumberSerializer extends NumberSerializer {
    /**
     * Derived from JS Number.MAX_SAFE_INTEGER and Number.MIN_SAFE_INTEGER
     */
    private static final long MAX_SAFE_INTEGER = 9007199254740991L;
    private static final long MIN_SAFE_INTEGER = -9007199254740991L;

    /**
     * Provide instance
     */
    public static final BigNumberSerializer MY_INSTANCE = new BigNumberSerializer(Number.class);

    public BigNumberSerializer(Class<? extends Number> rawType) {
        super(rawType);
    }

    @Override
    public void serialize(Number value, JsonGenerator gen, SerializationContext context) {
        // Out-of-range Serialization bit string
        if (value.longValue() > MIN_SAFE_INTEGER && value.longValue() < MAX_SAFE_INTEGER) {
            super.serialize(value, gen, context);
        } else {
            gen.writeString(value.toString());
        }
    }

}
