myTestRule(*arg1) {
  delayExec (*arg1) {
    msiDeleteUnusedAVUs;
  }
}
INPUT *arg1="<PLUSET>1m</PLUSET><EF>24h</EF>"
OUTPUT ruleExecOut
