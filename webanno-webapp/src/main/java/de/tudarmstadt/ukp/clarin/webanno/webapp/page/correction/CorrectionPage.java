/*******************************************************************************
 * Copyright 2012
 * Ubiquitous Knowledge Processing (UKP) Lab and FG Language Technology
 * Technische Universität Darmstadt
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 ******************************************************************************/
package de.tudarmstadt.ukp.clarin.webanno.webapp.page.correction;

import static de.tudarmstadt.ukp.clarin.webanno.brat.controller.BratAjaxCasUtil.selectByAddr;
import static org.apache.uima.fit.util.JCasUtil.selectFollowing;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import javax.persistence.NoResultException;

import org.apache.commons.lang.exception.ExceptionUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.uima.UIMAException;
import org.apache.uima.jcas.JCas;
import org.apache.wicket.ajax.AjaxRequestTarget;
import org.apache.wicket.ajax.form.AjaxFormComponentUpdatingBehavior;
import org.apache.wicket.ajax.form.AjaxFormSubmitBehavior;
import org.apache.wicket.ajax.markup.html.AjaxLink;
import org.apache.wicket.extensions.ajax.markup.html.modal.ModalWindow;
import org.apache.wicket.markup.head.IHeaderResponse;
import org.apache.wicket.markup.head.OnLoadHeaderItem;
import org.apache.wicket.markup.html.basic.Label;
import org.apache.wicket.markup.html.form.Form;
import org.apache.wicket.markup.html.form.NumberTextField;
import org.apache.wicket.markup.html.panel.FeedbackPanel;
import org.apache.wicket.model.LoadableDetachableModel;
import org.apache.wicket.model.Model;
import org.apache.wicket.spring.injection.annot.SpringBean;
import org.springframework.dao.DataRetrievalFailureException;
import org.springframework.security.core.context.SecurityContextHolder;

import de.tudarmstadt.ukp.clarin.webanno.api.AnnotationService;
import de.tudarmstadt.ukp.clarin.webanno.api.RepositoryService;
import de.tudarmstadt.ukp.clarin.webanno.brat.annotation.BratAnnotator;
import de.tudarmstadt.ukp.clarin.webanno.brat.annotation.BratAnnotatorModel;
import de.tudarmstadt.ukp.clarin.webanno.brat.controller.BratAjaxCasUtil;
import de.tudarmstadt.ukp.clarin.webanno.brat.controller.BratAnnotationException;
import de.tudarmstadt.ukp.clarin.webanno.brat.curation.AnnotationSelection;
import de.tudarmstadt.ukp.clarin.webanno.brat.curation.component.CurationViewPanel;
import de.tudarmstadt.ukp.clarin.webanno.brat.curation.component.model.CurationBuilder;
import de.tudarmstadt.ukp.clarin.webanno.brat.curation.component.model.CurationContainer;
import de.tudarmstadt.ukp.clarin.webanno.brat.curation.component.model.CurationUserSegmentForAnnotationDocument;
import de.tudarmstadt.ukp.clarin.webanno.brat.curation.component.model.CurationViewForSourceDocument;
import de.tudarmstadt.ukp.clarin.webanno.brat.project.PreferencesUtil;
import de.tudarmstadt.ukp.clarin.webanno.brat.util.BratAnnotatorUtility;
import de.tudarmstadt.ukp.clarin.webanno.brat.util.CuratorUtil;
import de.tudarmstadt.ukp.clarin.webanno.model.AnnotationDocument;
import de.tudarmstadt.ukp.clarin.webanno.model.AnnotationDocumentState;
import de.tudarmstadt.ukp.clarin.webanno.model.Mode;
import de.tudarmstadt.ukp.clarin.webanno.model.SourceDocument;
import de.tudarmstadt.ukp.clarin.webanno.model.User;
import de.tudarmstadt.ukp.clarin.webanno.webapp.dialog.OpenModalWindowPanel;
import de.tudarmstadt.ukp.clarin.webanno.webapp.home.page.ApplicationPageBase;
import de.tudarmstadt.ukp.clarin.webanno.webapp.page.annotation.component.AnnotationLayersModalPanel;
import de.tudarmstadt.ukp.clarin.webanno.webapp.page.annotation.component.DocumentNamePanel;
import de.tudarmstadt.ukp.clarin.webanno.webapp.page.annotation.component.ExportModalPanel;
import de.tudarmstadt.ukp.clarin.webanno.webapp.page.annotation.component.FinishImage;
import de.tudarmstadt.ukp.clarin.webanno.webapp.page.annotation.component.FinishLink;
import de.tudarmstadt.ukp.clarin.webanno.webapp.page.annotation.component.GuidelineModalPanel;
import de.tudarmstadt.ukp.clarin.webanno.webapp.page.welcome.WelcomePage;
import de.tudarmstadt.ukp.dkpro.core.api.segmentation.type.Sentence;
import wicket.contrib.input.events.EventType;
import wicket.contrib.input.events.InputBehavior;
import wicket.contrib.input.events.key.KeyType;

/**
 * This is the main class for the correction page. Displays in the lower panel the Automatically
 * annotated document and in the upper panel the corrected annotation
 *
 * @author Seid Muhie Yimam
 */
public class CorrectionPage
    extends ApplicationPageBase
{
    private static final Log LOG = LogFactory.getLog(CorrectionPage.class);    
    
    private static final long serialVersionUID = 1378872465851908515L;

    @SpringBean(name = "documentRepository")
    private RepositoryService repository;

    @SpringBean(name = "annotationService")
    private AnnotationService annotationService;

    private CurationContainer curationContainer;
    private BratAnnotatorModel bModel;

    private Label numberOfPages;
    private DocumentNamePanel documentNamePanel;

    private int sentenceNumber = 1;
    private int totalNumberOfSentence;

    private long currentprojectId;

    // Open the dialog window on first load
    boolean firstLoad = true;

    private NumberTextField<Integer> gotoPageTextField;
    private int gotoPageAddress;

    private FinishImage finish;

    private CurationViewPanel automateView;
    private BratAnnotator mergeVisualizer;

    private Map<String, Map<Integer, AnnotationSelection>> annotationSelectionByUsernameAndAddress = new HashMap<String, Map<Integer, AnnotationSelection>>();

    private CurationViewForSourceDocument curationSegment = new CurationViewForSourceDocument();

    public CorrectionPage()
    {
        bModel = new BratAnnotatorModel();
        bModel.setMode(Mode.CORRECTION);

        LinkedList<CurationUserSegmentForAnnotationDocument> sentences = new LinkedList<CurationUserSegmentForAnnotationDocument>();
        CurationUserSegmentForAnnotationDocument curationUserSegmentForAnnotationDocument = new CurationUserSegmentForAnnotationDocument();
        if (bModel.getDocument() != null) {
            curationUserSegmentForAnnotationDocument
                    .setAnnotationSelectionByUsernameAndAddress(annotationSelectionByUsernameAndAddress);
            curationUserSegmentForAnnotationDocument.setBratAnnotatorModel(bModel);
            sentences.add(curationUserSegmentForAnnotationDocument);
        }
        automateView = new CurationViewPanel("automateView",
                new Model<LinkedList<CurationUserSegmentForAnnotationDocument>>(sentences))
        {
            private static final long serialVersionUID = 2583509126979792202L;

            @Override
            public void onChange(AjaxRequestTarget aTarget)
            {
                try {
                    // update begin/end of the curationsegment based on bratAnnotatorModel changes
                    // (like sentence change in auto-scroll mode,....
                    aTarget.addChildren(getPage(), FeedbackPanel.class);
                    curationContainer.setBratAnnotatorModel(bModel);
                    setCurationSegmentBeginEnd();

                    CuratorUtil.updatePanel(aTarget, this, curationContainer, mergeVisualizer,
                            repository, annotationSelectionByUsernameAndAddress, curationSegment,
                            annotationService);
                }
                catch (UIMAException e) {
                    error(ExceptionUtils.getRootCause(e));
                }
                catch (ClassNotFoundException e) {
                    error(e.getMessage());
                }
                catch (IOException e) {
                    error(e.getMessage());
                }
                catch (BratAnnotationException e) {
                    error(e.getMessage());
                }
                mergeVisualizer.bratRenderLater(aTarget);
                aTarget.add(numberOfPages);
                update(aTarget);
            }
        };

        automateView.setOutputMarkupId(true);
        add(automateView);

        mergeVisualizer = new BratAnnotator("mergeView", new Model<BratAnnotatorModel>(
                bModel))
        {
            private static final long serialVersionUID = 7279648231521710155L;

            @Override
            protected void onChange(AjaxRequestTarget aTarget,
                    BratAnnotatorModel aBratAnnotatorModel)
            {
                try {
                    aTarget.addChildren(getPage(), FeedbackPanel.class);
//                    info(bratAnnotatorModel.getMessage());
                    bModel = aBratAnnotatorModel;
                    CurationBuilder builder = new CurationBuilder(repository);
                    curationContainer = builder.buildCurationContainer(bModel);
                    setCurationSegmentBeginEnd();
                    curationContainer.setBratAnnotatorModel(bModel);

                    CuratorUtil.updatePanel(aTarget, automateView, curationContainer, this,
                            repository, annotationSelectionByUsernameAndAddress, curationSegment,
                            annotationService);
                    aTarget.add(automateView);
                    aTarget.add(numberOfPages);
                }
                catch (UIMAException e) {
                    error(ExceptionUtils.getRootCause(e));
                }
                catch (ClassNotFoundException e) {
                    error(e.getMessage());
                }
                catch (IOException e) {
                    error(e.getMessage());
                }
                catch (BratAnnotationException e) {
                    error(e.getMessage());
                }
                update(aTarget);
            }
        };
        // reset sentenceAddress and lastSentenceAddress to the orginal once

        mergeVisualizer.setOutputMarkupId(true);
        add(mergeVisualizer);

        curationContainer = new CurationContainer();
        curationContainer.setBratAnnotatorModel(bModel);

        add(documentNamePanel = new DocumentNamePanel("documentNamePanel",
                new Model<BratAnnotatorModel>(bModel)));

        add(numberOfPages = (Label) new Label("numberOfPages",
                new LoadableDetachableModel<String>()
                {
                    private static final long serialVersionUID = 891566759811286173L;

                    @Override
                    protected String load()
                    {
                        if (bModel.getDocument() != null) {

                            JCas mergeJCas = null;
                            try {

                                mergeJCas = repository
                                        .getCorrectionDocumentContent(bModel
                                                .getDocument());

                                totalNumberOfSentence = BratAjaxCasUtil.getNumberOfPages(mergeJCas);

                                // If only one page, start displaying from sentence 1
                                /*
                                 * if (totalNumberOfSentence == 1) {
                                 * bratAnnotatorModel.setSentenceAddress(bratAnnotatorModel
                                 * .getFirstSentenceAddress()); }
                                 */
                                int address = BratAjaxCasUtil.selectSentenceAt(mergeJCas,
                                        bModel.getSentenceBeginOffset(),
                                        bModel.getSentenceEndOffset()).getAddress();
                                sentenceNumber = BratAjaxCasUtil.getFirstSentenceNumber(mergeJCas,
                                        address);
                                int firstSentenceNumber = sentenceNumber + 1;
                                int lastSentenceNumber;
                                if (firstSentenceNumber + bModel.getWindowSize() - 1 < totalNumberOfSentence) {
                                    lastSentenceNumber = firstSentenceNumber
                                            + bModel.getWindowSize() - 1;
                                }
                                else {
                                    lastSentenceNumber = totalNumberOfSentence;
                                }

                                return "showing " + firstSentenceNumber + "-" + lastSentenceNumber
                                        + " of " + totalNumberOfSentence + " sentences";
                            }
                            catch (UIMAException e) {
                                return "";
                            }
                            catch (DataRetrievalFailureException e) {
                                return "";
                            }
                            catch (ClassNotFoundException e) {
                                return "";
                            }
                            catch (FileNotFoundException e) {
                                return "";
                            }
                            catch (IOException e) {
                                return "";
                            }

                        }
                        else {
                            return "";// no document yet selected
                        }

                    }
                }).setOutputMarkupId(true));

        final ModalWindow openDocumentsModal;
        add(openDocumentsModal = new ModalWindow("openDocumentsModal"));
        openDocumentsModal.setOutputMarkupId(true);

        openDocumentsModal.setInitialWidth(500);
        openDocumentsModal.setInitialHeight(300);
        openDocumentsModal.setResizable(true);
        openDocumentsModal.setWidthUnit("px");
        openDocumentsModal.setHeightUnit("px");
        openDocumentsModal.setTitle("Open document");

        // Add project and document information at the top
        add(new AjaxLink<Void>("showOpenDocumentModal")
        {
            private static final long serialVersionUID = 7496156015186497496L;

            @Override
            public void onClick(AjaxRequestTarget aTarget)
            {
                openDocumentsModal.setContent(new OpenModalWindowPanel(openDocumentsModal
                        .getContentId(), bModel, openDocumentsModal, Mode.CORRECTION));
                openDocumentsModal.setWindowClosedCallback(new ModalWindow.WindowClosedCallback()
                {
                    private static final long serialVersionUID = -1746088901018629567L;

                    @Override
                    public void onClose(AjaxRequestTarget target)
                    {
                        if (bModel.getDocument() == null) {
                            setResponsePage(WelcomePage.class);
                            return;
                        }

                        try {
                            target.addChildren(getPage(), FeedbackPanel.class);
                            bModel.setDocument(bModel.getDocument());
                            bModel.setProject(bModel.getProject());

                            loadDocumentAction();
                            setCurationSegmentBeginEnd();
                            update(target);

                        }
                        catch (UIMAException e) {
                            target.appendJavaScript("alert('"+e.getMessage()+"')");
                            setResponsePage(WelcomePage.class);
                        }
                        catch (ClassNotFoundException e) {
                            target.appendJavaScript("alert('"+e.getMessage()+"')");
                            setResponsePage(WelcomePage.class);
                        }
                        catch (IOException e) {
                            target.appendJavaScript("alert('"+e.getMessage()+"')");
                            setResponsePage(WelcomePage.class);
                        }
                        catch (BratAnnotationException e) {
                            target.appendJavaScript("alert('"+e.getMessage()+"')");
                            setResponsePage(WelcomePage.class);
                        }
                        finish.setModelObject(bModel);
                        target.add(finish.setOutputMarkupId(true));
                        target.appendJavaScript("Wicket.Window.unloadConfirmation=false;window.location.reload()");
                        target.add(documentNamePanel.setOutputMarkupId(true));
                        target.add(numberOfPages);
                    }
                });
                openDocumentsModal.show(aTarget);
            }
        });

        add(new AnnotationLayersModalPanel("annotationLayersModalPanel",
                new Model<BratAnnotatorModel>(bModel))
        {
            private static final long serialVersionUID = -4657965743173979437L;

            @Override
            protected void onChange(AjaxRequestTarget aTarget)
            {
                curationContainer.setBratAnnotatorModel(bModel);
                try {
                    aTarget.addChildren(getPage(), FeedbackPanel.class);
                    setCurationSegmentBeginEnd();
                }
                catch (UIMAException e) {
                    error(ExceptionUtils.getRootCauseMessage(e));
                }
                catch (ClassNotFoundException e) {
                    error(e.getMessage());
                }
                catch (IOException e) {
                    error(e.getMessage());
                }
                update(aTarget);
                // mergeVisualizer.reloadContent(aTarget);
                aTarget.appendJavaScript("Wicket.Window.unloadConfirmation = false;window.location.reload()");

            }
        });

        add(new ExportModalPanel("exportModalPanel", new Model<BratAnnotatorModel>(
                bModel)));

        gotoPageTextField = (NumberTextField<Integer>) new NumberTextField<Integer>("gotoPageText",
                new Model<Integer>(0));
        Form<Void> gotoPageTextFieldForm = new Form<Void>("gotoPageTextFieldForm");
        gotoPageTextFieldForm.add(new AjaxFormSubmitBehavior(gotoPageTextFieldForm, "onsubmit") {
			private static final long serialVersionUID = -4549805321484461545L;
			@Override
            protected void onSubmit(AjaxRequestTarget aTarget) {
				 if (gotoPageAddress == 0) {
	                    aTarget.appendJavaScript("alert('The sentence number entered is not valid')");
	                    return;
	                }
			        JCas mergeJCas = null;
	                try {
	                    aTarget.addChildren(getPage(), FeedbackPanel.class);
	                    mergeJCas = repository.getCorrectionDocumentContent(bModel
	                            .getDocument());
	                    if (bModel.getSentenceAddress() != gotoPageAddress) {
	                        bModel.setSentenceAddress(gotoPageAddress);

	                        Sentence sentence = selectByAddr(mergeJCas, Sentence.class, gotoPageAddress);
	                        bModel.setSentenceBeginOffset(sentence.getBegin());
	                        bModel.setSentenceEndOffset(sentence.getEnd());

	                        CurationBuilder builder = new CurationBuilder(repository);
	                        curationContainer = builder.buildCurationContainer(bModel);
	                        setCurationSegmentBeginEnd();
	                        curationContainer.setBratAnnotatorModel(bModel);
	                        update(aTarget);
	                        mergeVisualizer.bratRenderLater(aTarget);
	                    }
	                }
	                catch (UIMAException e) {
	                    error(ExceptionUtils.getRootCause(e));
	                }
	                catch (ClassNotFoundException e) {
	                    error(e.getMessage());
	                }
	                catch (IOException e) {
	                    error(e.getMessage());
	                }
	                catch (BratAnnotationException e) {
	                    error(e.getMessage());
	                }
            }
        });

        gotoPageTextField.setType(Integer.class);
        gotoPageTextField.setMinimum(1);
        gotoPageTextField.setDefaultModelObject(1);
        add(gotoPageTextFieldForm.add(gotoPageTextField));
        gotoPageTextField.add(new AjaxFormComponentUpdatingBehavior("onchange")
        {
            private static final long serialVersionUID = -3853194405966729661L;

            @Override
            protected void onUpdate(AjaxRequestTarget target)
            {
                JCas mergeJCas = null;
                try {
                    mergeJCas = repository.getCorrectionDocumentContent(bModel
                            .getDocument());
                    gotoPageAddress = BratAjaxCasUtil.getSentenceAddress(mergeJCas,
                            gotoPageTextField.getModelObject());
                }
                catch (UIMAException e) {
                    error(ExceptionUtils.getRootCause(e));
                }
                catch (ClassNotFoundException e) {
                    error(ExceptionUtils.getRootCause(e));
                }
                catch (IOException e) {
                    error(e.getMessage());
                }

            }
        });

        add(new AjaxLink<Void>("gotoPageLink")
        {
            private static final long serialVersionUID = 7496156015186497496L;

            @Override
            public void onClick(AjaxRequestTarget aTarget)
            {

                if (gotoPageAddress == 0) {
                    aTarget.appendJavaScript("alert('The sentence number entered is not valid')");
                    return;
                }
                if (bModel.getDocument() == null) {
                    aTarget.appendJavaScript("alert('Please open a document first!')");
                    return;
                }
                JCas mergeJCas = null;
                try {
                    aTarget.addChildren(getPage(), FeedbackPanel.class);
                    mergeJCas = repository.getCorrectionDocumentContent(bModel
                            .getDocument());
                    if (bModel.getSentenceAddress() != gotoPageAddress) {
                        bModel.setSentenceAddress(gotoPageAddress);

                        Sentence sentence = selectByAddr(mergeJCas, Sentence.class, gotoPageAddress);
                        bModel.setSentenceBeginOffset(sentence.getBegin());
                        bModel.setSentenceEndOffset(sentence.getEnd());

                        CurationBuilder builder = new CurationBuilder(repository);
                        curationContainer = builder.buildCurationContainer(bModel);
                        setCurationSegmentBeginEnd();
                        curationContainer.setBratAnnotatorModel(bModel);
                        update(aTarget);
                        mergeVisualizer.bratRenderLater(aTarget);
                    }
                }
                catch (UIMAException e) {
                    error(ExceptionUtils.getRootCause(e));
                }
                catch (ClassNotFoundException e) {
                    error(e.getMessage());
                }
                catch (IOException e) {
                    error(e.getMessage());
                }
                catch (BratAnnotationException e) {
                    error(e.getMessage());
                }
            }
        });

        finish = new FinishImage("finishImage", new LoadableDetachableModel<BratAnnotatorModel>()
        {
            private static final long serialVersionUID = -2737326878793568454L;

            @Override
            protected BratAnnotatorModel load()
            {
                return bModel;
            }
        });

        add(new FinishLink("showYesNoModalPanel",
                new Model<BratAnnotatorModel>(bModel), finish)
        {
            private static final long serialVersionUID = -4657965743173979437L;
        });

        // Show the previous document, if exist
        add(new AjaxLink<Void>("showPreviousDocument")
        {
            private static final long serialVersionUID = 7496156015186497496L;

            /**
             * Get the current beginning sentence address and add on it the size of the display
             * window
             */
            @Override
            public void onClick(AjaxRequestTarget target)
            {
                target.addChildren(getPage(), FeedbackPanel.class);
                // List of all Source Documents in the project
                List<SourceDocument> listOfSourceDocuements = repository
                        .listSourceDocuments(bModel.getProject());

                User user = repository.getUser(SecurityContextHolder.getContext()
                        .getAuthentication().getName());

                List<SourceDocument> sourceDocumentsinIgnorState = new ArrayList<SourceDocument>();
                for (SourceDocument sourceDocument : listOfSourceDocuements) {
                    if (repository.existsAnnotationDocument(sourceDocument, user)
                            && repository.getAnnotationDocument(sourceDocument, user).getState()
                                    .equals(AnnotationDocumentState.IGNORE)) {
                        sourceDocumentsinIgnorState.add(sourceDocument);
                    }
                }

                listOfSourceDocuements.removeAll(sourceDocumentsinIgnorState);

                // Index of the current source document in the list
                int currentDocumentIndex = listOfSourceDocuements.indexOf(bModel
                        .getDocument());

                // If the first the document
                if (currentDocumentIndex == 0) {
                    target.appendJavaScript("alert('This is the first document!')");
                }
                else {
                    bModel.setDocumentName(listOfSourceDocuements.get(
                            currentDocumentIndex - 1).getName());
                    bModel.setDocument(listOfSourceDocuements
                            .get(currentDocumentIndex - 1));

                    try {
                        loadDocumentAction();
                        setCurationSegmentBeginEnd();
                        update(target);

                    }
                    catch (UIMAException e) {
                        error(ExceptionUtils.getRootCause(e));
                    }
                    catch (ClassNotFoundException e) {
                        error(ExceptionUtils.getRootCause(e));
                    }
                    catch (IOException e) {
                        error(ExceptionUtils.getRootCause(e));
                    }
                    catch (BratAnnotationException e) {
                        target.addChildren(getPage(), FeedbackPanel.class);
                        error(e.getMessage());
                    }

                    finish.setModelObject(bModel);
                    target.add(finish.setOutputMarkupId(true));
                    target.add(documentNamePanel);
                    mergeVisualizer.bratRenderLater(target);
                }
            }
        }.add(new InputBehavior(new KeyType[] { KeyType.Shift, KeyType.Page_up }, EventType.click)));

        // Show the next document if exist
        add(new AjaxLink<Void>("showNextDocument")
        {
            private static final long serialVersionUID = 7496156015186497496L;

            /**
             * Get the current beginning sentence address and add on it the size of the display
             * window
             */
            @Override
            public void onClick(AjaxRequestTarget target)
            {
                target.addChildren(getPage(), FeedbackPanel.class);
                // List of all Source Documents in the project
                List<SourceDocument> listOfSourceDocuements = repository
                        .listSourceDocuments(bModel.getProject());

                String username = SecurityContextHolder.getContext().getAuthentication().getName();
                User user = repository.getUser(username);

                List<SourceDocument> sourceDocumentsinIgnorState = new ArrayList<SourceDocument>();
                for (SourceDocument sourceDocument : listOfSourceDocuements) {
                    if (repository.existsAnnotationDocument(sourceDocument, user)
                            && repository.getAnnotationDocument(sourceDocument, user).getState()
                                    .equals(AnnotationDocumentState.IGNORE)) {
                        sourceDocumentsinIgnorState.add(sourceDocument);
                    }
                }

                listOfSourceDocuements.removeAll(sourceDocumentsinIgnorState);

                // Index of the current source document in the list
                int currentDocumentIndex = listOfSourceDocuements.indexOf(bModel
                        .getDocument());

                // If the first document
                if (currentDocumentIndex == listOfSourceDocuements.size() - 1) {
                    target.appendJavaScript("alert('This is the last document!')");
                    return;
                }
                bModel.setDocumentName(listOfSourceDocuements.get(
                        currentDocumentIndex + 1).getName());
                bModel
                        .setDocument(listOfSourceDocuements.get(currentDocumentIndex + 1));

                try {
                    loadDocumentAction();
                    setCurationSegmentBeginEnd();
                    update(target);

                }
                catch (UIMAException e) {
                    error(ExceptionUtils.getRootCause(e));
                }
                catch (ClassNotFoundException e) {
                    error(ExceptionUtils.getRootCause(e));
                }
                catch (IOException e) {
                    error(ExceptionUtils.getRootCause(e));
                }
                catch (BratAnnotationException e) {
                    target.addChildren(getPage(), FeedbackPanel.class);
                    error(e.getMessage());
                }
                catch (Exception e) {
                    target.addChildren(getPage(), FeedbackPanel.class);
                    error(e.getMessage());
                }

                finish.setModelObject(bModel);
                target.add(finish.setOutputMarkupId(true));
                target.add(documentNamePanel);
                mergeVisualizer.bratRenderLater(target);
            }
        }.add(new InputBehavior(new KeyType[] { KeyType.Shift, KeyType.Page_down }, EventType.click)));

        // Show the next page of this document
        add(new AjaxLink<Void>("showNext")
        {
            private static final long serialVersionUID = 7496156015186497496L;

            /**
             * Get the current beginning sentence address and add on it the size of the display
             * window
             */
            @Override
            public void onClick(AjaxRequestTarget aTarget)
            {
                if (bModel.getDocument() != null) {
                    JCas mergeJCas = null;
                    try {
                        aTarget.addChildren(getPage(), FeedbackPanel.class);
                        mergeJCas = repository.getCorrectionDocumentContent(bModel
                                .getDocument());
                        int address = BratAjaxCasUtil.selectSentenceAt(mergeJCas,
                                bModel.getSentenceBeginOffset(),
                                bModel.getSentenceEndOffset()).getAddress();
                        int nextSentenceAddress = BratAjaxCasUtil
                                .getNextPageFirstSentenceAddress(mergeJCas, address,
                                        bModel.getWindowSize());
                        if (address != nextSentenceAddress) {
                            bModel.setSentenceAddress(nextSentenceAddress);

                            Sentence sentence = selectByAddr(mergeJCas, Sentence.class,
                                    nextSentenceAddress);
                            bModel.setSentenceBeginOffset(sentence.getBegin());
                            bModel.setSentenceEndOffset(sentence.getEnd());

                            CurationBuilder builder = new CurationBuilder(repository);
                            curationContainer = builder.buildCurationContainer(bModel);
                            setCurationSegmentBeginEnd();
                            curationContainer.setBratAnnotatorModel(bModel);
                            update(aTarget);
                            mergeVisualizer.bratRenderLater(aTarget);
                        }

                        else {
                            aTarget.appendJavaScript("alert('This is last page!')");
                        }
                    }
                    catch (UIMAException e) {
                        aTarget.addChildren(getPage(), FeedbackPanel.class);
                        error(ExceptionUtils.getRootCause(e));
                    }
                    catch (ClassNotFoundException e) {
                        aTarget.addChildren(getPage(), FeedbackPanel.class);
                        error(e.getMessage());
                    }
                    catch (IOException e) {
                        aTarget.addChildren(getPage(), FeedbackPanel.class);
                        error(e.getMessage());
                    }
                    catch (BratAnnotationException e) {
                        aTarget.addChildren(getPage(), FeedbackPanel.class);
                        error(e.getMessage());
                    }
                    catch (Exception e) {
                        aTarget.addChildren(getPage(), FeedbackPanel.class);
                        error(e.getMessage());
                    }
                }
                else {
                    aTarget.appendJavaScript("alert('Please open a document first!')");
                }
            }
        }.add(new InputBehavior(new KeyType[] { KeyType.Page_down }, EventType.click)));

        // SHow the previous page of this document
        add(new AjaxLink<Void>("showPrevious")
        {
            private static final long serialVersionUID = 7496156015186497496L;

            @Override
            public void onClick(AjaxRequestTarget aTarget)
            {
                if (bModel.getDocument() != null) {

                    JCas mergeJCas = null;
                    try {
                        aTarget.addChildren(getPage(), FeedbackPanel.class);
                        mergeJCas = repository.getCorrectionDocumentContent(bModel
                                .getDocument());
                        int previousSentenceAddress = BratAjaxCasUtil
                                .getPreviousDisplayWindowSentenceBeginAddress(mergeJCas,
                                        bModel.getSentenceAddress(),
                                        bModel.getWindowSize());
                        if (bModel.getSentenceAddress() != previousSentenceAddress) {
                            bModel.setSentenceAddress(previousSentenceAddress);

                            Sentence sentence = selectByAddr(mergeJCas, Sentence.class,
                                    previousSentenceAddress);
                            bModel.setSentenceBeginOffset(sentence.getBegin());
                            bModel.setSentenceEndOffset(sentence.getEnd());

                            CurationBuilder builder = new CurationBuilder(repository);

                            curationContainer = builder.buildCurationContainer(bModel);
                            setCurationSegmentBeginEnd();
                            curationContainer.setBratAnnotatorModel(bModel);
                            update(aTarget);
                            mergeVisualizer.bratRenderLater(aTarget);
                        }
                        else {
                            aTarget.appendJavaScript("alert('This is First Page!')");
                        }
                    }
                    catch (UIMAException e) {
                        aTarget.addChildren(getPage(), FeedbackPanel.class);
                        error(ExceptionUtils.getRootCause(e));
                    }
                    catch (ClassNotFoundException e) {
                        aTarget.addChildren(getPage(), FeedbackPanel.class);
                        error(e.getMessage());
                    }
                    catch (IOException e) {
                        aTarget.addChildren(getPage(), FeedbackPanel.class);
                        error(e.getMessage());
                    }
                    catch (BratAnnotationException e) {
                        aTarget.addChildren(getPage(), FeedbackPanel.class);
                        error(e.getMessage());
                    }
                    catch (Exception e) {
                        aTarget.addChildren(getPage(), FeedbackPanel.class);
                        error(e.getMessage());
                    }
                }
                else {
                    aTarget.appendJavaScript("alert('Please open a document first!')");
                }
            }
        }.add(new InputBehavior(new KeyType[] { KeyType.Page_up }, EventType.click)));

        add(new AjaxLink<Void>("showFirst")
        {
            private static final long serialVersionUID = 7496156015186497496L;

            @Override
            public void onClick(AjaxRequestTarget aTarget)
            {
                if (bModel.getDocument() != null) {
                    JCas mergeJCas = null;
                    try {
                        aTarget.addChildren(getPage(), FeedbackPanel.class);
                        mergeJCas = repository.getCorrectionDocumentContent(bModel
                                .getDocument());

                        int address = BratAjaxCasUtil.selectSentenceAt(mergeJCas,
                                bModel.getSentenceBeginOffset(),
                                bModel.getSentenceEndOffset()).getAddress();
                        int firstAddress = BratAjaxCasUtil.getFirstSentenceAddress(mergeJCas);

                        if (firstAddress != address) {
                            bModel.setSentenceAddress(firstAddress);

                            Sentence sentence = selectByAddr(mergeJCas, Sentence.class,
                                    firstAddress);
                            bModel.setSentenceBeginOffset(sentence.getBegin());
                            bModel.setSentenceEndOffset(sentence.getEnd());

                            CurationBuilder builder = new CurationBuilder(repository);
                            curationContainer = builder.buildCurationContainer(bModel);
                            setCurationSegmentBeginEnd();
                            curationContainer.setBratAnnotatorModel(bModel);
                            update(aTarget);
                            mergeVisualizer.bratRenderLater(aTarget);
                        }
                        else {
                            aTarget.appendJavaScript("alert('This is first page!')");
                        }
                    }
                    catch (UIMAException e) {
                        aTarget.addChildren(getPage(), FeedbackPanel.class);
                        error(ExceptionUtils.getRootCause(e));
                    }
                    catch (ClassNotFoundException e) {
                        aTarget.addChildren(getPage(), FeedbackPanel.class);
                        error(e.getMessage());
                    }
                    catch (IOException e) {
                        aTarget.addChildren(getPage(), FeedbackPanel.class);
                        error(e.getMessage());
                    }
                    catch (BratAnnotationException e) {
                        aTarget.addChildren(getPage(), FeedbackPanel.class);
                        error(e.getMessage());
                    }
                    catch (Exception e) {
                        aTarget.addChildren(getPage(), FeedbackPanel.class);
                        error(e.getMessage());
                    }
                }
                else {
                    aTarget.appendJavaScript("alert('Please open a document first!')");
                }
            }
        }.add(new InputBehavior(new KeyType[] { KeyType.Home }, EventType.click)));

        add(new AjaxLink<Void>("showLast")
        {
            private static final long serialVersionUID = 7496156015186497496L;

            @Override
            public void onClick(AjaxRequestTarget aTarget)
            {
                if (bModel.getDocument() != null) {
                    JCas mergeJCas = null;
                    try {
                        aTarget.addChildren(getPage(), FeedbackPanel.class);
                        mergeJCas = repository.getCorrectionDocumentContent(bModel
                                .getDocument());
                        int lastDisplayWindowBeginingSentenceAddress = BratAjaxCasUtil
                                .getLastDisplayWindowFirstSentenceAddress(mergeJCas,
                                        bModel.getWindowSize());
                        if (lastDisplayWindowBeginingSentenceAddress != bModel
                                .getSentenceAddress()) {
                            bModel
                                    .setSentenceAddress(lastDisplayWindowBeginingSentenceAddress);

                            Sentence sentence = selectByAddr(mergeJCas, Sentence.class,
                                    lastDisplayWindowBeginingSentenceAddress);
                            bModel.setSentenceBeginOffset(sentence.getBegin());
                            bModel.setSentenceEndOffset(sentence.getEnd());

                            CurationBuilder builder = new CurationBuilder(repository);
                            curationContainer = builder.buildCurationContainer(bModel);
                            setCurationSegmentBeginEnd();
                            curationContainer.setBratAnnotatorModel(bModel);
                            update(aTarget);
                            mergeVisualizer.bratRenderLater(aTarget);

                        }
                        else {
                            aTarget.appendJavaScript("alert('This is last Page!')");
                        }
                    }
                    catch (UIMAException e) {
                        aTarget.addChildren(getPage(), FeedbackPanel.class);
                        error(ExceptionUtils.getRootCause(e));
                    }
                    catch (ClassNotFoundException e) {
                        aTarget.addChildren(getPage(), FeedbackPanel.class);
                        error(e.getMessage());
                    }
                    catch (IOException e) {
                        aTarget.addChildren(getPage(), FeedbackPanel.class);
                        error(e.getMessage());
                    }
                    catch (BratAnnotationException e) {
                        aTarget.addChildren(getPage(), FeedbackPanel.class);
                        error(e.getMessage());
                    }
                    catch (Exception e) {
                        aTarget.addChildren(getPage(), FeedbackPanel.class);
                        error(e.getMessage());
                    }
                }
                else {
                    aTarget.appendJavaScript("alert('Please open a document first!')");
                }
            }
        }.add(new InputBehavior(new KeyType[] { KeyType.End }, EventType.click)));

        add(new GuidelineModalPanel("guidelineModalPanel", new Model<BratAnnotatorModel>(
                bModel)));
    }

    /**
     * for the first time the page is accessed, open the <b>open document dialog</b>
     */
    @Override
    public void renderHead(IHeaderResponse response)
    {
        super.renderHead(response);
        
        String jQueryString = "";
        if (firstLoad) {
            jQueryString += "jQuery('#showOpenDocumentModal').trigger('click');";
            firstLoad = false;
        }
        response.render(OnLoadHeaderItem.forScript(jQueryString));
        if (bModel.getProject() != null) {

            mergeVisualizer.setModelObject(bModel);
            mergeVisualizer.setCollection("#" + bModel.getProject().getName() + "/");
            mergeVisualizer.bratInitRenderLater(response);

        }

    }

    private void loadDocumentAction()
        throws UIMAException, ClassNotFoundException, IOException, BratAnnotationException
    {
        User logedInUser = repository.getUser(SecurityContextHolder.getContext()
                .getAuthentication().getName());
        
        bModel.setUser(logedInUser);
        

        JCas jCas = null;
        try {
            AnnotationDocument annotationDocument = repository.getAnnotationDocument(
                    bModel.getDocument(), logedInUser);
            jCas = repository.getAnnotationDocumentContent(annotationDocument);
            repository.upgrade(jCas.getCas(), bModel.getProject());      
            repository.upgrade( repository.getCorrectionDocumentContent(bModel.getDocument()).getCas(), bModel.getProject());
            jCas = repository.getAnnotationDocumentContent(annotationDocument);

        }
        catch (UIMAException e) {
            throw e;
        }
        catch (ClassNotFoundException e) {
            throw e;
        }
        // Raised if the annotation CAS do not exist while annotation document entry is in DB
        catch (IOException e) {
            AnnotationDocument annoDoc = repository.getAnnotationDocument(
                    bModel.getDocument(), logedInUser);
            jCas = repository.createJCas(bModel.getDocument(),
                    annoDoc, bModel.getProject(), logedInUser);
            repository.createCorrectionDocumentContent(jCas, bModel.getDocument(),
                    logedInUser);
            repository.upgradeCasAndSave(bModel.getDocument(), Mode.CORRECTION,
                    logedInUser.getUsername());
            jCas = BratAnnotatorUtility.clearJcasAnnotations(repository.getAnnotationDocumentContent(annoDoc),
                    bModel.getDocument(), logedInUser, repository);
        }
        // Get information to be populated to bratAnnotatorModel from the JCAS of the logged in user
        // or from the previous correction document
        catch (DataRetrievalFailureException e) {
            if (repository.existsCorrectionDocument(bModel.getDocument())) {
                jCas = repository.getCorrectionDocumentContent(bModel.getDocument());
                // remove all annotation so that the user can correct from the auto annotation

                AnnotationDocument annotationDocument;
                // if annotation Document created out side of correction project (such as
                // Monitoring)
                if (repository.existsAnnotationDocument(bModel.getDocument(),
                        logedInUser)) {
                    annotationDocument = repository.getAnnotationDocument(
                            bModel.getDocument(), logedInUser);
                }
                else {
                    annotationDocument = new AnnotationDocument();
                    annotationDocument.setDocument(bModel.getDocument());
                    annotationDocument.setName(bModel.getDocument().getName());
                    annotationDocument.setUser(logedInUser.getUsername());
                    annotationDocument.setProject(bModel.getProject());
                    repository.createAnnotationDocument(annotationDocument);
                }
                repository.createAnnotationDocumentContent(jCas, bModel.getDocument(), logedInUser);
                // upgrade the cas
                repository.upgradeCasAndSave(bModel.getDocument(), Mode.CORRECTION,
                        logedInUser.getUsername());  
                
                jCas = BratAnnotatorUtility.clearJcasAnnotations(repository.getAnnotationDocumentContent(annotationDocument),
                        bModel.getDocument(), logedInUser, repository);
            }
            else {
                jCas = repository.readJCas(bModel.getDocument(), bModel
                        .getDocument().getProject(), logedInUser);
                // upgrade the cas
                repository.upgradeCasAndSave(bModel.getDocument(), Mode.CORRECTION,
                        logedInUser.getUsername());
                // This is the auto annotation, save it under CURATION_USER
                repository.createCorrectionDocumentContent(jCas, bModel.getDocument(),
                        logedInUser);
                // remove all annotation so that the user can correct from the auto annotation
                jCas = BratAnnotatorUtility.clearJcasAnnotations(jCas,
                        bModel.getDocument(), logedInUser, repository);
            }
        }
        catch (NoResultException e) {
            if (repository.existsCorrectionDocument(bModel.getDocument())) {
                jCas = repository.getCorrectionDocumentContent(bModel.getDocument());
                // remove all annotation so that the user can correct from the auto annotation

                AnnotationDocument annotationDocument;
                // if annotation Document created out side of correction project (such as
                // Monitoring)
                if (repository.existsAnnotationDocument(bModel.getDocument(),
                        logedInUser)) {
                    annotationDocument = repository.getAnnotationDocument(
                            bModel.getDocument(), logedInUser);
                }
                else {
                    annotationDocument = new AnnotationDocument();
                    annotationDocument.setDocument(bModel.getDocument());
                    annotationDocument.setName(bModel.getDocument().getName());
                    annotationDocument.setUser(logedInUser.getUsername());
                    annotationDocument.setProject(bModel.getProject());
                    repository.createAnnotationDocument(annotationDocument);
                }
                repository.createAnnotationDocumentContent(jCas, bModel.getDocument(), logedInUser);

             // upgrade the cas
                repository.upgradeCasAndSave(bModel.getDocument(), Mode.CORRECTION,
                        logedInUser.getUsername());
                
                jCas = BratAnnotatorUtility.clearJcasAnnotations(repository.getAnnotationDocumentContent(annotationDocument),
                        bModel.getDocument(), logedInUser, repository);
            }
            else {
                jCas = repository.readJCas(bModel.getDocument(), bModel
                        .getDocument().getProject(), logedInUser);
                
             // upgrade the cas
                repository.upgradeCasAndSave(bModel.getDocument(), Mode.CORRECTION,
                        logedInUser.getUsername());
                // This is the auto annotation, save it under CURATION_USER
                repository.createCorrectionDocumentContent(jCas, bModel.getDocument(),
                        logedInUser);
                // remove all annotation so that the user can correct from the auto annotation
                jCas = BratAnnotatorUtility.clearJcasAnnotations(jCas,
                        bModel.getDocument(), logedInUser, repository);
            }
        }
        
        // (Re)initialize brat model after potential creating / upgrading CAS
        bModel.initForDocument(jCas);

        // Load user preferences
        PreferencesUtil.setAnnotationPreference(logedInUser.getUsername(), repository,
                annotationService, bModel, Mode.CORRECTION);

        // if project is changed, reset some project specific settings
        if (currentprojectId != bModel.getProject().getId()) {
            bModel.initForProject();
        }

        currentprojectId = bModel.getProject().getId();
        
        LOG.debug("Configured BratAnnotatorModel for user [" + bModel.getUser()
                + "] f:[" + bModel.getFirstSentenceAddress() + "] l:["
                + bModel.getLastSentenceAddress() + "] s:["
                + bModel.getSentenceAddress() + "]");
    }

    private void setCurationSegmentBeginEnd()
        throws UIMAException, ClassNotFoundException, IOException
    {
        JCas jCas = repository.readJCas(bModel.getDocument(),
                bModel.getProject(), bModel.getUser());

        final int sentenceAddress = BratAjaxCasUtil.selectSentenceAt(jCas,
                bModel.getSentenceBeginOffset(),
                bModel.getSentenceEndOffset()).getAddress();

        final Sentence sentence = selectByAddr(jCas, Sentence.class, sentenceAddress);
        List<Sentence> followingSentences = selectFollowing(jCas, Sentence.class, sentence,
                bModel.getWindowSize());
        // Check also, when getting the last sentence address in the display window, if this is the
        // last sentence or the ONLY sentence in the document
        Sentence lastSentenceAddressInDisplayWindow = followingSentences.size() == 0 ? sentence
                : followingSentences.get(followingSentences.size() - 1);
        curationSegment.setBegin(sentence.getBegin());
        curationSegment.setEnd(lastSentenceAddressInDisplayWindow.getEnd());

    }

    private void update(AjaxRequestTarget target)
    {
        JCas correctionDocument = null;
        try {
            correctionDocument = CuratorUtil.updatePanel(target, automateView, curationContainer,
                    mergeVisualizer, repository, annotationSelectionByUsernameAndAddress,
                    curationSegment, annotationService);
        }
        catch (UIMAException e) {
            error(ExceptionUtils.getRootCauseMessage(e));
        }
        catch (ClassNotFoundException e) {
            error(e.getMessage());
        }
        catch (IOException e) {
            error(e.getMessage());
        }
        catch (BratAnnotationException e) {
            error(e.getMessage());
        }

        gotoPageTextField.setModelObject(BratAjaxCasUtil.getFirstSentenceNumber(correctionDocument,
                bModel.getSentenceAddress()) + 1);
        gotoPageAddress = BratAjaxCasUtil.getSentenceAddress(correctionDocument,
                gotoPageTextField.getModelObject());

        target.add(gotoPageTextField);
        target.add(automateView);
        target.add(numberOfPages);
    }

}