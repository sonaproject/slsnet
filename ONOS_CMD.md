# Usefull ONOS Console Commands
이용재


(command, arguments auto fill: TAB을 치면 해당 자리에 들어갈 수 있는 command 나 arugment 항목을 보여줌)

onos:devices
- device 정보 출력

onos:annotate-(type)
- 해당 type의 entry에 key value 정보 항목을 추가; onos:devices, links, ports 에서 보여짐
- annotate-device of:0000000000000001 name SS1
- annotate-port of:0000000000000001/1 portName toLS1
- ? annotate-link of:0000000000000001/1 of:000000000000000a/7 name SS1-LS1