package com.etribunal.core.cases;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "cases")
public class CaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, columnDefinition = "varchar(10)")
    private CaseType type;

    @Column(name = "category", nullable = false)
    private String category = "Other";

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, columnDefinition = "varchar(10)")
    private CaseStatus status = CaseStatus.WAITING;

    @Column(name = "title", nullable = false, length = 100)
    private String title;

    @Column(name = "side_a_content", nullable = false, columnDefinition = "text")
    private String sideAContent;

    @Column(name = "side_b_content", columnDefinition = "text")
    private String sideBContent;

    @Column(name = "side_a_subtitle", length = 50)
    private String sideASubtitle;

    @Column(name = "side_b_subtitle", length = 50)
    private String sideBSubtitle;

    @Column(name = "both_wrong_subtitle", length = 50)
    private String bothWrongSubtitle;

    @Column(name = "content_language", nullable = false, length = 10)
    private String contentLanguage = "es";

    @Column(name = "invite_token", unique = true, length = 36)
    private String inviteToken;

    @Column(name = "is_anonymous", nullable = false)
    private boolean anonymous;

    @Column(name = "is_private", nullable = false)
    private boolean isPrivate;

    @Column(name = "total_votes", nullable = false)
    private int totalVotes;

    @Column(name = "votes_a", nullable = false)
    private int votesA;

    @Column(name = "votes_b", nullable = false)
    private int votesB;

    @Column(name = "votes_both_wrong", nullable = false)
    private int votesBothWrong;

    @Column(name = "total_comments", nullable = false)
    private int totalComments;

    @Column(name = "total_views", nullable = false)
    private int totalViews;

    @Column(name = "total_shares", nullable = false)
    private int totalShares;

    @Column(name = "total_anchors", nullable = false)
    private int totalAnchors;

    @Enumerated(EnumType.STRING)
    @Column(name = "moderation_status", nullable = false, columnDefinition = "varchar(10)")
    private ModerationStatus moderationStatus = ModerationStatus.PENDING;

    @Column(name = "risk_score")
    private Double riskScore;

    @Column(name = "side_a_user_id", nullable = false)
    private UUID sideAUserId;

    @Column(name = "side_b_user_id")
    private UUID sideBUserId;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public CaseType getType() {
        return type;
    }

    public void setType(CaseType type) {
        this.type = type;
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public CaseStatus getStatus() {
        return status;
    }

    public void setStatus(CaseStatus status) {
        this.status = status;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getSideAContent() {
        return sideAContent;
    }

    public void setSideAContent(String sideAContent) {
        this.sideAContent = sideAContent;
    }

    public String getSideBContent() {
        return sideBContent;
    }

    public void setSideBContent(String sideBContent) {
        this.sideBContent = sideBContent;
    }

    public String getSideASubtitle() {
        return sideASubtitle;
    }

    public void setSideASubtitle(String sideASubtitle) {
        this.sideASubtitle = sideASubtitle;
    }

    public String getSideBSubtitle() {
        return sideBSubtitle;
    }

    public void setSideBSubtitle(String sideBSubtitle) {
        this.sideBSubtitle = sideBSubtitle;
    }

    public String getBothWrongSubtitle() {
        return bothWrongSubtitle;
    }

    public void setBothWrongSubtitle(String bothWrongSubtitle) {
        this.bothWrongSubtitle = bothWrongSubtitle;
    }

    public String getContentLanguage() {
        return contentLanguage;
    }

    public void setContentLanguage(String contentLanguage) {
        this.contentLanguage = contentLanguage;
    }

    public String getInviteToken() {
        return inviteToken;
    }

    public void setInviteToken(String inviteToken) {
        this.inviteToken = inviteToken;
    }

    public boolean isAnonymous() {
        return anonymous;
    }

    public void setAnonymous(boolean anonymous) {
        this.anonymous = anonymous;
    }

    public boolean isPrivate() {
        return isPrivate;
    }

    public void setPrivate(boolean isPrivate) {
        this.isPrivate = isPrivate;
    }

    public int getTotalVotes() {
        return totalVotes;
    }

    public void setTotalVotes(int totalVotes) {
        this.totalVotes = totalVotes;
    }

    public int getVotesA() {
        return votesA;
    }

    public void setVotesA(int votesA) {
        this.votesA = votesA;
    }

    public int getVotesB() {
        return votesB;
    }

    public void setVotesB(int votesB) {
        this.votesB = votesB;
    }

    public int getVotesBothWrong() {
        return votesBothWrong;
    }

    public void setVotesBothWrong(int votesBothWrong) {
        this.votesBothWrong = votesBothWrong;
    }

    public int getTotalComments() {
        return totalComments;
    }

    public void setTotalComments(int totalComments) {
        this.totalComments = totalComments;
    }

    public int getTotalViews() {
        return totalViews;
    }

    public void setTotalViews(int totalViews) {
        this.totalViews = totalViews;
    }

    public int getTotalShares() {
        return totalShares;
    }

    public void setTotalShares(int totalShares) {
        this.totalShares = totalShares;
    }

    public int getTotalAnchors() {
        return totalAnchors;
    }

    public void setTotalAnchors(int totalAnchors) {
        this.totalAnchors = totalAnchors;
    }

    public ModerationStatus getModerationStatus() {
        return moderationStatus;
    }

    public Double getRiskScore() {
        return riskScore;
    }

    public UUID getSideAUserId() {
        return sideAUserId;
    }

    public void setSideAUserId(UUID sideAUserId) {
        this.sideAUserId = sideAUserId;
    }

    public UUID getSideBUserId() {
        return sideBUserId;
    }

    public void setSideBUserId(UUID sideBUserId) {
        this.sideBUserId = sideBUserId;
    }

    public Instant getDeletedAt() {
        return deletedAt;
    }

    public void setDeletedAt(Instant deletedAt) {
        this.deletedAt = deletedAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
