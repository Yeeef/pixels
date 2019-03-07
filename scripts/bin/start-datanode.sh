#!/bin/bash

echo "Start Pixels Datanode."

base_dir=$(dirname $0)
MAIN_CLASS="cn.edu.ruc.iir.pixels.daemon.DaemonMain"

if [ "xPIXELS_HOME" = "x" ]; then
  export PIXELS_HOME="${base_dir}/../"
fi

EXTRA_ARGS="-role main -name PixelsCoordinator"
OPTS="datanode"
COMMAND=$1
case ${COMMAND} in
  -daemon)
    EXTRA_ARGS=${EXTRA_ARGS}" -daemon"
    shift
    ;;
  *)
    ;;
esac

exec ${base_dir}/run-class.sh ${EXTRA_ARGS} ${MAIN_CLASS} $OPTS
