/*
 * Copyright (c) 2005 JetBrains s.r.o. All Rights Reserved.
 */
package com.intellij.codeInsight.daemon;

import com.intellij.CommonBundle;

import java.util.ResourceBundle;

import org.jetbrains.annotations.NonNls;

/**
 * @author max
 */
public class QuickFixBundle {
  private static final ResourceBundle ourBundle = ResourceBundle.getBundle("com.intellij.codeInsight.daemon.QuickFixBundle");

  private QuickFixBundle() {}

  public static String message(@NonNls String key, Object... params) {
    return CommonBundle.message(ourBundle, key, params);
  }
}
