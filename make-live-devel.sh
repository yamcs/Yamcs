#!/bin/sh
#creates a runnable environment that allow to run yamcs from the command line using links to the development tree
# the configuration files and the runnable shell files from the bin directory are not overwritten

# the script has to be run each time the version number is changed, and has to run when everything is properly compiled from the command line



#set this to where you want the live "installation" to be performed
TARGET="live"
#Allow overriding default from the command line
if [ -n "$1" ] ; then
        TARGET="$1"
fi


YAMCS_HOME=`pwd`

mkdir -p $TARGET/etc
mkdir -p $TARGET/bin

rm -rf $TARGET/lib
mkdir -p $TARGET/lib/ext


for f in $YAMCS_HOME/yamcs-core/etc/* ; do
    case "$f" in
        *.sample)
            FILENAME=$(basename "$f")
            cp -an "$f" $TARGET/etc/${FILENAME%.*}
            ;;
        *)
            cp -an "$f" $TARGET/etc/
            ;;
    esac
done
for f in $YAMCS_HOME/yamcs-core/bin/* ; do
    cp -an "$f" $TARGET/bin/
done
for f in $YAMCS_HOME/yamcs-simulation/bin/* ; do
    cp -an "$f" $TARGET/bin/
done
ln -fs $YAMCS_HOME/yamcs-core/target/*.jar $TARGET/lib
ln -fs $YAMCS_HOME/yamcs-core/lib/*.jar $TARGET/lib

ln -fs $YAMCS_HOME/yamcs-simulation/target/*.jar $TARGET/lib

ln -fs $YAMCS_HOME/yamcs-web/target/*.jar $TARGET/lib
ln -fs $YAMCS_HOME/yamcs-web/target/dependency/*.jar $TARGET/lib
ln -fs $YAMCS_HOME/yamcs-web/src/main/resources/mime.types $TARGET/etc


mkdir -p $TARGET/mdb
ln -fs $YAMCS_HOME/yamcs-core/mdb/* $TARGET/mdb
ln -fs $YAMCS_HOME/yamcs-simulation/mdb/* $TARGET/mdb

cp -an $YAMCS_HOME/yamcs-simulation/etc/mdb.yaml $TARGET/etc
ln -fs $YAMCS_HOME/yamcs-simulation/etc/yamcs.simulator.yaml $TARGET/etc

cp -an $YAMCS_HOME/yamcs-simulation/bin/simulator.sh $TARGET/bin
ln -fs $YAMCS_HOME/yamcs-simulation/test_data $TARGET/



if [ -f make-live-devel-local.sh ] ; then
 sh make-live-devel-local.sh $TARGET
fi
