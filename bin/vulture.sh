#!/bin/bash

if [ $# -ne 1 ]; then
  echo "Usage: $0 <screen_session_name>"
  exit -1
fi

SESSION_NAME=$1

# Create a new screen session in detached mode
screen -S $SESSION_NAME -X quit
screen -d -m -S $SESSION_NAME

# For each subdirectory in the current directory, create a new screen window and launch job
for f in $PWD/*; do
  if [ -d $f ]; then
    for idealDRAM in $(seq 0 1); do
      SUFFIX="unk"

      if [ $idealDRAM -eq 0 ]; then
        SUFFIX="baseline"
      else
        SUFFIX="n3xt"
      fi

      CMD="cd $f; USE_IDEAL_DRAM=$idealDRAM bash run.sh $(cat args) | tee ${SUFFIX}.log"

      # Window title for session to be created
      WINDOW_TITLE=${f}_$SUFFIX

      # Creates a new screen window with the above title in existing screen session
      screen -S $SESSION_NAME -X screen -t $WINDOW_TITLE

      # Switch terminal to bash
      screen -S $SESSION_NAME -p $WINDOW_TITLE -X stuff "bash$(printf \\r)"

      # Launch $CMD in newly created screen window
      screen -S $SESSION_NAME -p $WINDOW_TITLE -X stuff "$CMD$(printf \\r)"

      # Acknowledge the screen exists and log if it doesn't
      # logger "something"

      # Sleep because this may fix clobbering bug
      sleep 0.2

    done
  fi
done
