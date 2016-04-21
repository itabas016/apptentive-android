/*
 * Copyright (c) 2016, Apptentive, Inc. All Rights Reserved.
 * Please refer to the LICENSE file for the terms and conditions
 * under which redistribution and use of this file is permitted.
 */

package com.apptentive.android.sdk.module.engagement.interaction.fragment;

import android.app.Activity;
import android.content.Context;
import android.os.Bundle;
import android.os.Handler;
import android.support.v4.app.Fragment;
import android.support.v7.view.ContextThemeWrapper;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.apptentive.android.sdk.ApptentiveInternal;

import com.apptentive.android.sdk.ApptentiveLog;
import com.apptentive.android.sdk.R;
import com.apptentive.android.sdk.model.SurveyResponse;
import com.apptentive.android.sdk.module.engagement.EngagementModule;
import com.apptentive.android.sdk.module.engagement.interaction.model.SurveyInteraction;
import com.apptentive.android.sdk.module.engagement.interaction.model.survey.MultichoiceQuestion;
import com.apptentive.android.sdk.module.engagement.interaction.model.survey.MultiselectQuestion;
import com.apptentive.android.sdk.module.engagement.interaction.model.survey.Question;
import com.apptentive.android.sdk.module.engagement.interaction.model.survey.SinglelineQuestion;
import com.apptentive.android.sdk.module.engagement.interaction.view.survey.BaseSurveyQuestionView;
import com.apptentive.android.sdk.module.engagement.interaction.view.survey.MultichoiceSurveyQuestionView;
import com.apptentive.android.sdk.module.engagement.interaction.view.survey.MultiselectSurveyQuestionView;
import com.apptentive.android.sdk.module.engagement.interaction.view.survey.SurveyQuestionView;
import com.apptentive.android.sdk.module.engagement.interaction.view.survey.TextSurveyQuestionView;
import com.apptentive.android.sdk.module.survey.OnSurveyFinishedListener;
import com.apptentive.android.sdk.module.survey.OnSurveyQuestionAnsweredListener;
import com.apptentive.android.sdk.util.Util;
import com.apptentive.android.sdk.view.ApptentiveNestedScrollView;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;


public class SurveyFragment extends ApptentiveBaseFragment<SurveyInteraction> implements OnSurveyQuestionAnsweredListener,
		ApptentiveNestedScrollView.OnScrollChangeListener {

	private static final String EVENT_CANCEL = "cancel";
	private static final String EVENT_CLOSE = "close";
	private static final String EVENT_SUBMIT = "submit";
	private static final String EVENT_QUESTION_RESPONSE = "question_response";

	private ApptentiveNestedScrollView scrollView;
	private LinearLayout questionsContainer;

	private Map<String, Object> answers;

	public static SurveyFragment newInstance(Bundle bundle) {
		SurveyFragment fragment = new SurveyFragment();
		fragment.setArguments(bundle);
		return fragment;
	}

	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
		if (interaction == null) {
			getActivity().finish();
		}

		List<Question> questions = interaction.getQuestions();
		answers = new LinkedHashMap<String, Object>(questions.size());

		// create ContextThemeWrapper from the original Activity Context with the apptentive theme
		final Context contextThemeWrapper = new ContextThemeWrapper(getActivity(), ApptentiveInternal.getInstance().getApptentiveTheme());
		// clone the inflater using the ContextThemeWrapper
		final LayoutInflater themedInflater = inflater.cloneInContext(contextThemeWrapper);
		View v = themedInflater.inflate(R.layout.apptentive_survey, container, false);

		TextView description = (TextView) v.findViewById(R.id.description);
		description.setText(interaction.getDescription());

		final Button send = (Button) v.findViewById(R.id.send);

		String sendText = interaction.getSubmitText();
		if (!TextUtils.isEmpty(sendText)) {
			send.setText(sendText);
		}
		send.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View view) {
				Util.hideSoftKeyboard(getActivity(), view);
				boolean valid = validateAndUpdateState();
				if (valid) {
					if (interaction.isShowSuccessMessage() && !TextUtils.isEmpty(interaction.getSuccessMessage())) {
						Toast toast = new Toast(contextThemeWrapper);
						toast.setGravity(Gravity.FILL, 0, 0);
						toast.setDuration(Toast.LENGTH_SHORT);
						View toastView = themedInflater.inflate(R.layout.apptentive_survey_sent_toast, (LinearLayout) getView().findViewById(R.id.survey_sent_toast_root));
						toast.setView(toastView);
						TextView actionTV = ((TextView) toastView.findViewById(R.id.survey_sent_action_text));
						actionTV.setText(interaction.getSuccessMessage());
            int actionColor = Util.getThemeColor(ApptentiveInternal.getInstance().getApptentiveTheme(), R.attr.apptentiveSurveySentToastActionColor);
						if (actionColor != 0) {
							actionTV.setTextColor(actionColor);
							ImageView actionIcon = (ImageView) toastView.findViewById(R.id.survey_sent_action_icon);
							actionIcon.setColorFilter(actionColor);
						}
						toast.show();
					}
					getActivity().finish();

					EngagementModule.engageInternal(getActivity(), interaction, EVENT_SUBMIT);

					ApptentiveInternal.getInstance().getApptentiveDatabase().addPayload(new SurveyResponse(interaction, answers));
					ApptentiveLog.d("Survey Submitted.");
					callListener(true);
				} else {
					Toast toast = new Toast(contextThemeWrapper);
					toast.setGravity(Gravity.FILL_HORIZONTAL | Gravity.BOTTOM, 0, 0);
					toast.setDuration(Toast.LENGTH_SHORT);
					View toastView = themedInflater.inflate(R.layout.apptentive_survey_invalid_toast, (LinearLayout) getView().findViewById(R.id.survey_invalid_toast_root));
					toast.setView(toastView);
					String validationText = interaction.getValidationError();
					if (!TextUtils.isEmpty(validationText)) {
						((TextView) toastView.findViewById(R.id.survey_invalid_toast_text)).setText(validationText);
					}
					toast.show();
				}
			}
		});

		questionsContainer = (LinearLayout) v.findViewById(R.id.questions);
		if (savedInstanceState == null) {
			questionsContainer.removeAllViews();

			// Then render all the questions
			for (final Question question : questions) {
				final BaseSurveyQuestionView surveyQuestionView;
				if (question.getType() == Question.QUESTION_TYPE_SINGLELINE) {
					surveyQuestionView = TextSurveyQuestionView.newInstance((SinglelineQuestion) question);
				} else if (question.getType() == Question.QUESTION_TYPE_MULTICHOICE) {
					surveyQuestionView = MultichoiceSurveyQuestionView.newInstance((MultichoiceQuestion) question);

				} else if (question.getType() == Question.QUESTION_TYPE_MULTISELECT) {
					surveyQuestionView = MultiselectSurveyQuestionView.newInstance((MultiselectQuestion) question);
				} else {
					surveyQuestionView = null;
				}
				if (surveyQuestionView != null) {
					surveyQuestionView.setOnSurveyQuestionAnsweredListener(this);
					getRetainedChildFragmentManager().beginTransaction().add(R.id.questions, surveyQuestionView, question.getId()).commit();
				}
			}
		} else {
			List<Fragment> fragments = getRetainedChildFragmentManager().getFragments();
			for (Fragment fragment : fragments) {
				BaseSurveyQuestionView questionFragment = (BaseSurveyQuestionView) fragment;
				questionFragment.setOnSurveyQuestionAnsweredListener(this);

			}
		}
		return v;
	}

	@Override
	public void onViewCreated(View view, Bundle savedInstanceState) {
		super.onViewCreated(view, savedInstanceState);
		ImageButton infoButton = (ImageButton) view.findViewById(R.id.info);
		infoButton.setOnClickListener(new View.OnClickListener() {
			public void onClick(final View view) {
				// Set info button not clickable when it was first clicked
				view.setClickable(false);
				getActivity().runOnUiThread(new Runnable() {
					@Override
					public void run() {
						final Handler handler = new Handler();
						handler.postDelayed(new Runnable() {
							@Override
							public void run() {
								view.setClickable(true);
							}
						}, 100);

					}
				});
				ApptentiveInternal.getInstance().showAboutInternal(getActivity(), false);
			}
		});
		scrollView = (ApptentiveNestedScrollView) view.findViewById(R.id.survey_scrollview);
		scrollView.setOnScrollChangeListener(this);

		/* Android's ScrollView (when scrolled or fling'd) by default always set the focus to an EditText when
		 * it's one of it's children.
		 * The following is needed to change this behavior such that touching outside EditText would take
		 * away the focus
		 */
		scrollView.setDescendantFocusability(ViewGroup.FOCUS_BEFORE_DESCENDANTS);
		scrollView.setFocusable(true);
		scrollView.setFocusableInTouchMode(true);
		scrollView.setOnTouchListener(new View.OnTouchListener() {
			@Override
			public boolean onTouch(View v, MotionEvent event) {
				v.requestFocusFromTouch();
				return false;
			}
		});

		getActivity().getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);

	}

	@Override
	public void onScrollChange(ApptentiveNestedScrollView v, int scrollX, int scrollY, int oldScrollX, int oldScrollY) {
		showToolbarElevation(v.getTop() != scrollY);
	}

	/**
	 * Run this when the user hits the send button, and only send if it returns true. This method will update the visual validation state of all questions, and update the answers instance variable with the latest answer state.
	 *
	 * @return true if all questions that have constraints have met those constraints.
	 */
	public boolean validateAndUpdateState() {
		boolean validationPassed = true;

		List<Fragment> fragments = getRetainedChildFragmentManager().getFragments();
		for (Fragment fragment : fragments) {
			SurveyQuestionView surveyQuestionView = (SurveyQuestionView) fragment;
			answers.put(surveyQuestionView.getQuestionId(), surveyQuestionView.getAnswer());
			boolean isValid = surveyQuestionView.isValid();
			surveyQuestionView.updateValidationState(isValid);
			if (!isValid) {
				validationPassed = false;
			}
		}
		return validationPassed;
	}

	void sendMetricForQuestion(Activity activity, String questionId) {
		JSONObject answerData = new JSONObject();
		try {
			answerData.put("id", questionId);
		} catch (JSONException e) {
			// Never happens.
		}
		EngagementModule.engageInternal(activity, interaction, EVENT_QUESTION_RESPONSE, answerData.toString());
	}

	private void callListener(boolean completed) {
		OnSurveyFinishedListener listener = ApptentiveInternal.getInstance().getOnSurveyFinishedListener();
		if (listener != null) {
			listener.onSurveyFinished(completed);
		}
	}

	@Override
	public boolean onBackPressed(boolean hardwareButton) {
		if (hardwareButton) {
			EngagementModule.engageInternal(getActivity(), interaction, EVENT_CANCEL);
		} else {
			EngagementModule.engageInternal(getActivity(), interaction, EVENT_CLOSE);
		}
		return false;
	}

	@Override
	public void onAnswered(SurveyQuestionView surveyQuestionView) {
		String questionId = surveyQuestionView.getQuestionId();
		if (!surveyQuestionView.didSendMetric()) {
			surveyQuestionView.setSentMetric(true);
			sendMetricForQuestion(getActivity(), questionId);
		}
		// Also clear validation state for questions that are no longer invalid.
		if (surveyQuestionView.isValid()) {
			surveyQuestionView.updateValidationState(true);
		}
	}

	@Override
	public int getToolbarNavigationIconResourceId() {
		// Survey uses close icon to replace up arrow on toolbar
		return Util.getResourceIdFromAttribute(ApptentiveInternal.getInstance().getApptentiveTheme(), R.attr.apptentiveToolbarIconClose);
	}

}