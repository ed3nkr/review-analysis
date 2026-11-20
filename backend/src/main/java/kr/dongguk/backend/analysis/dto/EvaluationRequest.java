package kr.dongguk.backend.analysis.dto;

/** 점주 평가: 몰랐던 사실인가 / 맞는가 / 행동하겠는가 (+ 한 줄 의견). null 이면 아직 고르지 않음 */
public record EvaluationRequest(Boolean evalNew, Boolean evalCorrect, Boolean evalAction, String evalComment) {}
