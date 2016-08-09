package com.pmease.gitplex.web.page.depot.pullrequest.requestdetail.overview.activity;

import org.apache.commons.lang3.StringUtils;
import org.apache.wicket.Component;
import org.apache.wicket.ajax.AjaxRequestTarget;
import org.apache.wicket.ajax.attributes.AjaxRequestAttributes;
import org.apache.wicket.ajax.markup.html.AjaxLink;
import org.apache.wicket.ajax.markup.html.form.AjaxButton;
import org.apache.wicket.behavior.AttributeAppender;
import org.apache.wicket.markup.html.WebMarkupContainer;
import org.apache.wicket.markup.html.basic.Label;
import org.apache.wicket.markup.html.form.Form;
import org.apache.wicket.markup.html.panel.Fragment;
import org.apache.wicket.markup.html.panel.GenericPanel;
import org.apache.wicket.model.AbstractReadOnlyModel;
import org.apache.wicket.model.IModel;
import org.apache.wicket.model.LoadableDetachableModel;
import org.apache.wicket.model.Model;
import org.hibernate.StaleObjectStateException;

import com.pmease.commons.wicket.ajaxlistener.ConfirmLeaveListener;
import com.pmease.commons.wicket.behavior.markdown.AttachmentSupport;
import com.pmease.commons.wicket.component.markdown.MarkdownEditSupport;
import com.pmease.commons.wicket.component.markdown.MarkdownPanel;
import com.pmease.commons.wicket.editable.EditableUtils;
import com.pmease.gitplex.core.GitPlex;
import com.pmease.gitplex.core.entity.Depot;
import com.pmease.gitplex.core.entity.PullRequest;
import com.pmease.gitplex.core.entity.PullRequestStatusChange;
import com.pmease.gitplex.core.manager.PullRequestStatusChangeManager;
import com.pmease.gitplex.core.security.SecurityUtils;
import com.pmease.gitplex.web.component.AccountLink;
import com.pmease.gitplex.web.component.avatar.AvatarLink;
import com.pmease.gitplex.web.component.comment.CommentInput;
import com.pmease.gitplex.web.component.comment.DepotAttachmentSupport;
import com.pmease.gitplex.web.page.depot.pullrequest.requestdetail.overview.SinceChangesLink;
import com.pmease.gitplex.web.util.DateUtils;

import de.agilecoders.wicket.core.markup.html.bootstrap.common.NotificationPanel;

@SuppressWarnings("serial")
class StatusChangePanel extends GenericPanel<PullRequestStatusChange> {

	public StatusChangePanel(String id, IModel<PullRequestStatusChange> model) {
		super(id, model);
	}

	private Component newViewer() {
		Fragment viewer = new Fragment("body", "viewFrag", this);
		
		String note = getStatusChange().getNote();
		if (StringUtils.isNotBlank(note)) {
			MarkdownEditSupport editSupport;
			if (SecurityUtils.canModify(getStatusChange())) {
				editSupport = new MarkdownEditSupport() {

					@Override
					public void setContent(String content) {
						getStatusChange().setNote(content);
						GitPlex.getInstance(PullRequestStatusChangeManager.class).save(getStatusChange());				
					}

					@Override
					public long getVersion() {
						return getStatusChange().getVersion();
					}
					
				};
			} else {
				editSupport = null;
			}
			viewer.add(new MarkdownPanel("content", new AbstractReadOnlyModel<String>() {

				@Override
				public String getObject() {
					return getStatusChange().getNote();
				}

			}, editSupport));
		} else {
			viewer.add(new Label("content", "<i>No note</i>").setEscapeModelStrings(false));
		}
		
		WebMarkupContainer actions = new WebMarkupContainer("actions");
		actions.setVisible(SecurityUtils.canModify(getStatusChange()));
		actions.add(new AjaxLink<Void>("edit") {

			@Override
			public void onClick(AjaxRequestTarget target) {
				Fragment editor = new Fragment("body", "editFrag", StatusChangePanel.this);
				
				Form<?> form = new Form<Void>("form");
				form.setOutputMarkupId(true);
				editor.add(form);
				
				NotificationPanel feedback = new NotificationPanel("feedback", form);
				feedback.setOutputMarkupPlaceholderTag(true);
				form.add(feedback);
				
				long lastVersion = getStatusChange().getVersion();
				CommentInput input = new CommentInput("input", Model.of(getStatusChange().getNote())) {

					@Override
					protected AttachmentSupport getAttachmentSupport() {
						return new DepotAttachmentSupport(getStatusChange().getRequest().getTargetDepot(), 
								getStatusChange().getRequest().getUUID());
					}

					@Override
					protected Depot getDepot() {
						return getStatusChange().getRequest().getTargetDepot();
					}
					
				};
				form.add(input);
				
				form.add(new AjaxButton("save") {

					@Override
					protected void onError(AjaxRequestTarget target, Form<?> form) {
						super.onError(target, form);
						target.add(feedback);
					}

					@Override
					protected void onSubmit(AjaxRequestTarget target, Form<?> form) {
						try {
							if (getStatusChange().getVersion() != lastVersion)
								throw new StaleObjectStateException(PullRequestStatusChange.class.getName(), getStatusChange().getId());
							getStatusChange().setNote(input.getModelObject());
							GitPlex.getInstance(PullRequestStatusChangeManager.class).save(getStatusChange());
	
							Component viewer = newViewer();
							editor.replaceWith(viewer);
							target.add(viewer);
						} catch (StaleObjectStateException e) {
							error("Some one changed the content you are editing. Reload the page and try again.");
							target.add(feedback);
						}
					}
					
				});
				
				form.add(new AjaxLink<Void>("cancel") {

					@Override
					protected void updateAjaxAttributes(AjaxRequestAttributes attributes) {
						super.updateAjaxAttributes(attributes);
						attributes.getAjaxCallListeners().add(new ConfirmLeaveListener(form));
					}
					
					@Override
					public void onClick(AjaxRequestTarget target) {
						Component viewer = newViewer();
						editor.replaceWith(viewer);
						target.add(viewer);
					}
					
				});
				
				editor.setOutputMarkupId(true);
				viewer.replaceWith(editor);
				target.add(editor);
			}

		});

		viewer.add(actions);
		
		viewer.setOutputMarkupId(true);
		return viewer;
	}
	
	@Override
	protected void onInitialize() {
		super.onInitialize();
		
		PullRequestStatusChange statusChange = getModelObject();
		
		WebMarkupContainer container = new WebMarkupContainer("statusChange");
		String activityName = EditableUtils.getName(statusChange.getEventType());
		container.add(AttributeAppender.append("class", "activity " + activityName.replace(" ", "-").toLowerCase()));
		
		WebMarkupContainer icon = new WebMarkupContainer("icon");
		container.add(icon);
		String iconClass = EditableUtils.getIcon(statusChange.getEventType());
		if (iconClass != null)
			icon.add(AttributeAppender.append("class", iconClass));
		
		container.add(new AvatarLink("userAvatar", statusChange.getUser(), null));
		container.add(new AccountLink("userName", statusChange.getUser()));
		container.add(new Label("eventName", EditableUtils.getName(statusChange.getEventType())));
		container.add(new Label("eventDate", DateUtils.formatAge(statusChange.getDate())));
		container.add(new SinceChangesLink("changes", new LoadableDetachableModel<PullRequest>() {

			@Override
			protected PullRequest load() {
				return StatusChangePanel.this.getModelObject().getRequest();
			}
			
		}, statusChange.getDate()));
		
		container.add(newViewer());
		
		add(container);
	}

	private PullRequestStatusChange getStatusChange() {
		return getModelObject();
	}
}