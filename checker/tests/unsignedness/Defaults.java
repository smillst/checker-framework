import org.checkerframework.checker.unsignedness.qual.*;

public class Defaults {

    public void ConstantTest() {

        //Test bytes with literal values
        @Constant byte conByte;
        @UnsignednessBottom byte botByte;

        byte testByte = 0;

        conByte = testByte;

        //:: error: (assignment.type.incompatible)
        botByte = testByte;

        //Test shorts with literal values
        @Constant short conShort;
        @UnsignednessBottom short botShort;

        short testShort = 128;

        conShort = testShort;

        //:: error: (assignment.type.incompatible)
        botShort = testShort;

        //Test ints with literal values
        @Constant int conInt;
        @UnsignednessBottom int botInt;

        int testInt = 32768;

        conInt = testInt;

        //:: error: (assignment.type.incompatible)
        botInt = testInt;

        //Test longs with literal values
        @Constant long conLong;
        @UnsignednessBottom long botLong;

        long testLong = 2147483648L;

        conLong = testLong;

        //:: error: (assignment.type.incompatible)
        botLong = testLong;

    }

    public void SignedTest(byte testByte, short testShort, int testInt, long testLong) {

        //Test bytes
        @Signed byte sinByte;
        @Constant byte conByte;

        sinByte = testByte;

        //:: error: (assignment.type.incompatible)
        conByte = testByte;

        //Test shorts
        @Signed short sinShort;
        @Constant short conShort;

        sinShort = testShort;

        //:: error: (assignment.type.incompatible)
        conShort = testShort;

        //Test ints
        @Signed int sinInt;
        @Constant int conInt;

        sinInt = testInt;

        //:: error: (assignment.type.incompatible)
        conInt = testInt;

        //Test longs
        @Signed long sinLong;
        @Constant long conLong;

        sinLong = testLong;

        //:: error: (assignment.type.incompatible)
        conLong = testLong;

    }

    public void UnsignednessBottom() {

        @UnsignednessBottom Object botObj;

        Object testObj = null;

        botObj = testObj;
    }

    public void UnknownSignedness(Object testObj) {

        @UnknownSignedness Object unkObj;
        @Signed Object sinObj;

        unkObj = testObj;

        //:: error: (assignment.type.incompatible)
        sinObj = testObj;
    }
}