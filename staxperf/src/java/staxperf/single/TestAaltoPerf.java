package staxperf.single;

import java.io.*;

import javax.xml.namespace.QName;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamReader;

//import com.ctc.wstx.api.WstxInputProperties;

public class TestAaltoPerf
    extends BasePerfTest
{
    protected TestAaltoPerf() { super(); }

    protected XMLInputFactory getFactory()
    {
        System.setProperty("javax.xml.stream.XMLInputFactory",
                           "org.codehaus.wool.stax.InputFactoryImpl");
        XMLInputFactory f =  XMLInputFactory.newInstance();
        return f;
    }

    // To test Char/Reader based parsing, uncomment:
    /*
    protected int testExec(byte[] data, String path) throws Exception
    {
        Reader r = new InputStreamReader(new ByteArrayInputStream(data), "UTF-8");
        XMLStreamReader sr = mFactory.createXMLStreamReader(r);
        int ret = testExec2(sr);
        return ret;
    }
    */

    public static void main(String[] args) throws Exception
    {
        new TestAaltoPerf().test(args);
    }
}
