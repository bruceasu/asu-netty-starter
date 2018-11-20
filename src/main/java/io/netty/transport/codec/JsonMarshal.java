package io.netty.transport.codec;


import com.alibaba.fastjson.JSONObject;
import me.asu.util.Bytes;

/**
 * JsonMarshal.
 * <p>2017 Suk All rights reserved.</p>
 *
 * @author Suk
 * @version 1.0.0
 * @since 2017-09-25 13:57
 */
public class JsonMarshal implements Marshal {

    @Override
    public int type() {
        return 1;
    }

    @Override
    public byte[] marshal(Object obj) {
        if (obj == null) {
            return new byte[0];
        } else if (obj instanceof String) {
            return Bytes.toBytes(obj.toString());
        } else {
            return Bytes.toBytes(JSONObject.toJSONString(obj));
        }
    }

    @Override
    public <T> T unmarshal(byte[] bytes, Class<T> type) {
        /*ignore type.just String.class*/
        return JSONObject.parseObject(Bytes.toString(bytes), type);
    }

    @Override
    public String toString() {
        return "JsonMarshal{type:1}";
    }
}
