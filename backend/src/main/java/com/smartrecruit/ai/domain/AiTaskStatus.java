package com.smartrecruit.ai.domain;

/** Analyse fonctionnelle §4.4 : PENDING → RUNNING → DONE ou FAILED ; DONE et FAILED sont définitifs. */
public enum AiTaskStatus { PENDING, RUNNING, DONE, FAILED }
