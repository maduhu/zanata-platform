package org.zanata.webtrans.shared.rpc;

import org.zanata.webtrans.shared.model.ProjectIterationId;


public class GetDocumentList extends AbstractWorkspaceAction<GetDocumentListResult>
{

   private static final long serialVersionUID = 1L;

   private ProjectIterationId projectIterationId;

   @SuppressWarnings("unused")
   private GetDocumentList()
   {
   }

   public GetDocumentList(ProjectIterationId id)
   {
      this.projectIterationId = id;
   }

   public ProjectIterationId getProjectIterationId()
   {
      return projectIterationId;
   }

   public void setProjectContainerId(ProjectIterationId projectIterationId)
   {
      this.projectIterationId = projectIterationId;
   }

}
