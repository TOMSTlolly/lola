package com.tomst.lolly.core;

// signalizace z threadu do hlavni formy
public enum TDevState
{
    tCapacity,
    tCheckTMSFirmware,
    tCompareTime,
    tError,
    tFinal,
    tFinishedData,
    tFirmware,
    tGetTime,
    tHead,
    tInfo,
    tInit,
    tLollyService,
    tMeasure,
    tNoHardware,
    tProgress,
    tReadData,
    tReadFromBookmark,
    tReadMeteo,
    tRemainDays,
    tSerial,
    tSerialDuplicity,
    tSetMeteo,
    tSetTime,
    tStart,
    tWaitForAdapter,
    tWaitForMeasure,
    tWaitInLimbo
}