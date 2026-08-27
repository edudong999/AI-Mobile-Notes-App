package com.ai_photo;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.rule.ActivityTestRule;
import com.ai_photo.ui.login.LoginActivity;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import static androidx.test.espresso.Espresso.*;
import static androidx.test.espresso.action.ViewActions.*;
import static androidx.test.espresso.assertion.ViewAssertions.*;
import static androidx.test.espresso.matcher.ViewMatchers.*;

@RunWith(AndroidJUnit4.class)
public class LoginFlowTest {
    @Rule
    public ActivityTestRule<LoginActivity> rule = new ActivityTestRule<>(LoginActivity.class);

    @Test
    public void login_screen_visible() {
        onView(withId(R.id.username)).check(matches(isDisplayed()));
        onView(withId(R.id.password)).check(matches(isDisplayed()));
        onView(withId(R.id.submit)).check(matches(isDisplayed()));
    }
}