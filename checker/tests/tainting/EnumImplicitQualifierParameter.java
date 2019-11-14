import org.checkerframework.checker.tainting.qual.Tainted;
import org.checkerframework.framework.qual.HasQualifierParameter;

@HasQualifierParameter(Tainted.class)
// :: error: (invalid.has.qual.param)
public enum EnumImplicitQualifierParameter {
    A,
    B
}
