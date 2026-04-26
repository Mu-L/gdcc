package gd.script.gdcc.backend.c;

import gd.script.gdcc.backend.c.build.ZigUtil;
import org.junit.jupiter.api.Test;

public class FindZigTest {
    @Test
    public void findZigTest() {
        var result = ZigUtil.findZig();
        System.out.println(result);
    }
}
