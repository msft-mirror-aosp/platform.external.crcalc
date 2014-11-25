CALC_CLASSES=CRCalc.class calc_msg.class command_queue.class \
command_queue_entry.class from_degrees_class.class time_slicer.class \
to_degrees_class.class
SOME_LIB_CLASSES=com/sgi/math/AbortedError.class com/sgi/math/CR.class \
com/sgi/math/PrecisionOverflowError.class com/sgi/math/TestCR.class \
com/sgi/math/UnaryCRFunction.class
LIB_SOURCES=UnaryCRFunction.java CR.java PrecisionOverflowError.java AbortedError.java
SOURCES=CRCalc.java TestCR.java rpn_calc.java $(LIB_SOURCES) \
CRCalc.html CRCalc-force.html instrs.html Makefile COPYRIGHT.txt impl.html
DOCFILES=com/sgi/math/UnaryCRFunction.html com/sgi/math/CR.html

JAVA_PREFIX=/usr/java/bin
JAVAC=$(JAVA_PREFIX)/javac
JAVADOC=$(JAVA_PREFIX)/javadoc
JAR=$(JAVA_PREFIX)/jar

all: classes.jar download/CRCalc.tar.gz $(DOCFILES)

classes.jar: $(CALC_CLASSES) $(SOME_LIB_CLASSES)
	$(JAR) cvf classes.jar $(CALC_CLASSES) com/sgi/math/*.class

$(CALC_CLASSES): CRCalc.java $(SOME_LIB_CLASSES)
	$(JAVAC) CRCalc.java

$(SOME_LIB_CLASSES): $(LIB_SOURCES) TestCR.java
	$(JAVAC) -d . $(LIB_SOURCES) TestCR.java

download/CRCalc.tar.gz: $(SOURCES) download
	rm -f download/CRCalc.tar.gz
	tar cvf download/CRCalc.tar $(SOURCES)
	gzip download/CRCalc.tar

download:
	mkdir download

# gcj native compiled version of the command line calculator.
# Requires installation of a recent gcj version.
# Should build with or without threads.
rpn_calc: rpn_calc.java $(LIB_SOURCES)
	gcj -O -o rpn_calc --main=rpn_calc rpn_calc.java $(LIB_SOURCES)

TestCR: TestCR.java $(LIB_SOURCES)
	gcj -O -o TestCR --main=com.sgi.math.TestCR $(LIB_SOURCES)

$(DOCFILES): CR.java UnaryCRFunction.java
	$(JAVADOC) -d . CR.java UnaryCRFunction.java
	rm -f tree.html
	rm -f packages.html
	rm -f AllNames.html

clean:
	rm -f com/sgi/math/*.class
	rm -f classes.jar $(DOCFILES)
	rm -f *.class
	rm -f rpn_calc
