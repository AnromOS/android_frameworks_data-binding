/*
 * Copyright (C) 2015 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package android.databinding.tool.expr;

import android.databinding.tool.reflection.Callable;
import android.databinding.tool.reflection.ModelAnalyzer;
import android.databinding.tool.reflection.ModelClass;
import android.databinding.tool.util.L;

import java.util.List;

public class FieldAccessExpr extends Expr {
    String mName;
    Callable mGetter;
    final boolean mIsObservableField;

    FieldAccessExpr(Expr parent, String name) {
        super(parent);
        mName = name;
        mIsObservableField = false;
    }

    FieldAccessExpr(Expr parent, String name, boolean isObservableField) {
        super(parent);
        mName = name;
        mIsObservableField = isObservableField;
    }

    public Expr getChild() {
        return getChildren().get(0);
    }

    public Callable getGetter() {
        if (mGetter == null) {
            getResolvedType();
        }
        return mGetter;
    }

    @Override
    public boolean isDynamic() {
        if (mGetter == null) {
            getResolvedType();
        }
        if (mGetter.type != Callable.Type.FIELD) {
            return true;
        }
        // if it is static final, gone
        if (getChild().isDynamic()) {
            // if owner is dynamic, then we can be dynamic unless we are static final
            return !mGetter.isStatic() || mGetter.isDynamic();
        }

        // if owner is NOT dynamic, we can be dynamic if an only if getter is dynamic
        return mGetter.isDynamic();
    }

    public boolean hasBindableAnnotations() {
        return mGetter.canBeInvalidated();
    }

    @Override
    protected List<Dependency> constructDependencies() {
        final List<Dependency> dependencies = constructDynamicChildrenDependencies();
        for (Dependency dependency : dependencies) {
            if (dependency.getOther() == getChild()) {
                dependency.setMandatory(true);
            }
        }
        return dependencies;
    }

    @Override
    protected String computeUniqueKey() {
        if (mIsObservableField) {
            return sUniqueKeyJoiner.join(mName, "..", super.computeUniqueKey());
        }
        return sUniqueKeyJoiner.join(mName, ".", super.computeUniqueKey());
    }

    public String getName() {
        return mName;
    }

    @Override
    public void updateExpr(ModelAnalyzer modelAnalyzer) {
        resolveType(modelAnalyzer);
        super.updateExpr(modelAnalyzer);
    }

    @Override
    protected ModelClass resolveType(ModelAnalyzer modelAnalyzer) {
        if (mGetter == null) {
            Expr child = getChild();
            child.resolveType(modelAnalyzer);
            boolean isStatic = child instanceof StaticIdentifierExpr;
            ModelClass resolvedType = child.getResolvedType();
            L.d("resolving %s. Resolved class type: %s", this, resolvedType);

            mGetter = resolvedType.findGetterOrField(mName, isStatic);

            if (mGetter.isStatic() && !isStatic) {
                // found a static method on an instance. register a new one
                child.getParents().remove(this);
                getChildren().remove(child);
                StaticIdentifierExpr staticId = getModel().staticIdentifierFor(resolvedType);
                getChildren().add(staticId);
                staticId.getParents().add(this);
                child = getChild(); // replace the child for the next if stmt
            }

            if (mGetter.resolvedType.isObservableField()) {
                // Make this the ".get()" and add an extra field access for the observable field
                child.getParents().remove(this);
                getChildren().remove(child);

                FieldAccessExpr observableField = getModel().observableField(child, mName);
                observableField.mGetter = mGetter;

                getChildren().add(observableField);
                observableField.getParents().add(this);
                mGetter = mGetter.resolvedType.findGetterOrField("get", false);
                mName = "";
            }
        }
        return mGetter.resolvedType;
    }

    @Override
    protected String asPackage() {
        String parentPackage = getChild().asPackage();
        return parentPackage == null ? null : parentPackage + "." + mName;
    }
}
