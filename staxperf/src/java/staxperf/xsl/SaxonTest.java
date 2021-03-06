package staxperf.xsl;

import java.io.*;

import javax.xml.transform.TransformerFactory;
import javax.xml.transform.stream.StreamResult;

import net.sf.saxon.TransformerFactoryImpl;

public final class SaxonTest
    extends TestBase
{
    private SaxonTest() { }

    protected TransformerFactory getFactory() {
        return new TransformerFactoryImpl();
    }

    protected StreamResult getResult(ByteArrayOutputStream bos)
        throws IOException
    {
        /* 16-Jan-2009, tatu: Hmmh. Shouldn't matter whether we
         *   wrap it ourself, or pass to Saxon... but seems to.
         */
        /*
        BufferedOutputStream buf = new BufferedOutputStream(bos);
        Writer w = new OutputStreamWriter(buf, "UTF-8");
        return new StreamResult(w);
        */

        return new StreamResult(bos);
    }

    public static void main(String[] args) throws Exception {
        new SaxonTest().test(args);
    }
}
