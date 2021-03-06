#!/bin/sh
 
echo "About to run full xsl test on parser+processor combos (input dir: $1)"

# Nothing big stored in memory, heap can remain modest 
# -Djapex.runTime=30 \
$JAVA_HOME/bin/java -server -cp lib/\* \
 -Xmx128M \
 -Djava.awt.headless=true \
 -Djapex.runsPerDriver=3 \
 -Djapex.warmupTime=5 \
 -Djapex.runTime=30 \
 -Djapex.numberOfThreads=1 \
 -Djapex.reportsDirectory=japex-reports \
 -Djapex.plotGroupSize=10 \
 -Djapex.inputDir="data/xsltmark-data" \
 com.sun.japex.Japex \
 testcfg/xslt-saxon.xml

echo "Done!";
