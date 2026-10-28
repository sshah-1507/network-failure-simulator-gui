@echo off
cd /d "%~dp0"
start "Network Simulator" javaw -cp out com.networksim.ui.NetworkGUI
