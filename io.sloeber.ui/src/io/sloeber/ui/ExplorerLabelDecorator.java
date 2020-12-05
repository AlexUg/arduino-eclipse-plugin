package io.sloeber.ui;

import org.eclipse.cdt.core.model.CoreModel;
import org.eclipse.cdt.core.settings.model.ICConfigurationDescription;
import org.eclipse.cdt.core.settings.model.ICProjectDescription;
import org.eclipse.core.resources.IProject;
import org.eclipse.jface.viewers.ILabelDecorator;
import org.eclipse.jface.viewers.ILabelProviderListener;
import org.eclipse.swt.graphics.Image;

import io.sloeber.core.api.SloeberProject;

public class ExplorerLabelDecorator implements ILabelDecorator {

	@Override
	public void addListener(ILabelProviderListener listener) {
		// nothing to do here

	}

	@Override
	public void dispose() {
		// nothing to do here

	}

	@Override
	public boolean isLabelProperty(Object element, String property) {
		return false;
	}

	@Override
	public void removeListener(ILabelProviderListener listener) {
		// nothing to do here

	}

	@Override
	public Image decorateImage(Image image, Object element) {
		return null;
	}

	@Override
	public String decorateText(String text, Object element) {
		IProject proj = (IProject) element;
		if (proj != null) {
			ICProjectDescription prjDesc = CoreModel.getDefault().getProjectDescription(proj);
			if (prjDesc != null) {
				ICConfigurationDescription confDesc = prjDesc.getActiveConfiguration();
				if (confDesc != null) {
					SloeberProject arduinoProject = SloeberProject
							.getSloeberProject(proj);
					if (arduinoProject != null) {
						return arduinoProject.getDecoratedText(confDesc, text);
					}
				}
			}
		}
		return text;
	}

}
