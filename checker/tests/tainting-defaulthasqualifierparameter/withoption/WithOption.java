package withoption;

import org.checkerframework.checker.tainting.qual.*;
import org.checkerframework.framework.qual.*;

public class WithOption {
    @PolyTainted int field;
}
