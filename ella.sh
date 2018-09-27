#!/bin/bash

ELLA_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"
mkdir -p "$ELLA_DIR/ella-out"

if [ "$1" == 'i' ]; then
shift
java -ea -classpath $ELLA_DIR/bin/ella.instrument.jar com.apposcopy.ella.EllaLauncher i $*
exit
fi

#start the server
if [ "$1" == 's' ]; then
java -ea -classpath $ELLA_DIR/bin/ella.server.jar com.apposcopy.ella.server.ServerController start
exit
fi

#start the server
if [ "$1" == 'k' ]; then
java -ea -classpath $ELLA_DIR/bin/ella.server.jar com.apposcopy.ella.server.ServerController kill
exit
fi