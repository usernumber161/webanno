/*
 * Copyright 2015
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
 */
package de.tudarmstadt.ukp.clarin.webanno.ui.annotation.detail.editor;

import org.apache.uima.cas.CAS;
import org.apache.wicket.MarkupContainer;
import org.apache.wicket.markup.html.basic.Label;
import org.apache.wicket.model.CompoundPropertyModel;
import org.apache.wicket.model.IModel;

import com.googlecode.wicket.kendo.ui.form.NumberTextField;

import de.tudarmstadt.ukp.clarin.webanno.api.annotation.model.FeatureState;

public class NumberFeatureEditor<T extends Number>
    extends FeatureEditor
{
    private static final long serialVersionUID = -2426303638953208057L;
    @SuppressWarnings("rawtypes")
    private final NumberTextField field;

    public NumberFeatureEditor(String aId, String aMarkupId, MarkupContainer aItem,
            IModel<FeatureState> aModel)
    {
        super(aId, aMarkupId, aItem, new CompoundPropertyModel<FeatureState>(aModel));

        add(new Label("feature", getModelObject().feature.getUiName()));

        switch (getModelObject().feature.getType()) {
        case CAS.TYPE_NAME_INTEGER: {
            field = new NumberTextField<Integer>("value", Integer.class);
            break;
        }
        case CAS.TYPE_NAME_FLOAT: {
            field = new NumberTextField<Float>("value", Float.class);
            add(field);
            break;
        }
        default:
            throw new IllegalArgumentException("Type [" + getModelObject().feature.getType()
                    + "] cannot be rendered as a numeric input field");
        }
        
        // Ensure that markup IDs of feature editor focus components remain constant across
        // refreshs of the feature editor panel. This is required to restore the focus.
        field.setOutputMarkupId(true);
        field.setMarkupId(ID_PREFIX + getModelObject().feature.getId());
        setOutputMarkupPlaceholderTag(true);
        
        add(field);
    }

    @SuppressWarnings("rawtypes")
    @Override
    public NumberTextField getFocusComponent()
    {
        return field;
    }
}