package manifestexcludes;

import java.io.File;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.jar.Attributes;
import java.util.jar.JarFile;
import java.util.jar.Manifest;

import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.core.runtime.Path;
import org.eclipse.jdt.core.IAccessRule;
import org.eclipse.jdt.core.IClasspathEntry;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.IPackageFragmentRoot;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jface.action.IAction;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.ui.IObjectActionDelegate;
import org.eclipse.ui.IWorkbenchPart;

public class OSGiRefreshAction implements IObjectActionDelegate {

	private ISelection selection;

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.eclipse.ui.IActionDelegate#run(org.eclipse.jface.action.IAction)
	 */
	public void run(IAction action) {
		if (selection instanceof IStructuredSelection) {
			for (Iterator it = ((IStructuredSelection) selection).iterator(); it
					.hasNext();) {
				try {
					Object element = it.next();
					if (element instanceof IJavaProject) {
						IJavaProject project = (IJavaProject) element;
						IClasspathEntry[] rawClasspath = project
								.getRawClasspath();
						IClasspathEntry[] newEntries = new IClasspathEntry[rawClasspath.length];
						for (int i = 0; i < rawClasspath.length; i++) {
							newEntries[i] = refreshClasspathEntry(project,
									rawClasspath[i]);
						}
						project.setRawClasspath(newEntries,
								new NullProgressMonitor());
					} else {
						System.out.println("shouldn't get here!");
					}
				} catch (Exception e) {
					e.printStackTrace();
				}
			}
		}
	}

	IClasspathEntry refreshClasspathEntry(IJavaProject project,
			IClasspathEntry ice) throws Exception {
		switch (ice.getEntryKind()) {
		case IClasspathEntry.CPE_LIBRARY:
			return refreshLibraryClasspathEntry(project, ice);
		case IClasspathEntry.CPE_VARIABLE:
			return refreshVariableClasspathEntry(project, ice);
		default:
			return ice;
		}
	}

	IClasspathEntry refreshLibraryClasspathEntry(IJavaProject project,
			IClasspathEntry ice) throws Exception {
		if (IPackageFragmentRoot.K_BINARY == ice.getContentKind()) {
			IAccessRule[] newRules = getAccessRules(ice);
			return JavaCore.newLibraryEntry(ice.getPath(),
					ice.getSourceAttachmentPath(),
					ice.getSourceAttachmentRootPath(), newRules,
					ice.getExtraAttributes(), ice.isExported());
		} else {
			System.out.println("isLibrary - source");
			return ice;
		}
	}

	IClasspathEntry refreshVariableClasspathEntry(IJavaProject project,
			IClasspathEntry ice) throws Exception {
		IClasspathEntry resolved = JavaCore.getResolvedClasspathEntry(ice);
		IAccessRule[] newRules = getAccessRules(resolved);
		return JavaCore.newVariableEntry(ice.getPath(),
				ice.getSourceAttachmentPath(),
				ice.getSourceAttachmentRootPath(), newRules,
				ice.getExtraAttributes(), ice.isExported());
	}

	IAccessRule[] getAccessRules(IClasspathEntry ice) throws Exception {
		IPath path = ice.getPath();
		File file = path.toFile();
		JarFile jf = new JarFile(file);
		Manifest mf = jf.getManifest();
		Attributes aa = mf.getMainAttributes();
		if (aa.getValue("Bundle-ManifestVersion") != null) {
			IAccessRule[] newRules = buildAccessRules(parseExportPackage(aa
					.getValue("Export-Package")));
			return newRules;
		} else {
			return ice.getAccessRules();
		}
	}

	String[] parseExportPackage(String value) {
		String[] parsed = parse(value);
		String[] withoutExtras = new String[parsed.length];
		for (int i = 0; i < withoutExtras.length; i++) {
			withoutExtras[i] = parsed[i].split(";")[0];
		}
		return withoutExtras;
	}

	IAccessRule[] buildAccessRules(String[] packages) {
		IAccessRule[] result = new IAccessRule[packages.length + 1];
		for (int i = 0; i < packages.length; i++) {
			result[i] = JavaCore.newAccessRule(
					new Path(packages[i].replace(".", "/") + "/*"),
					IAccessRule.K_ACCESSIBLE);
		}
		result[result.length - 1] = JavaCore.newAccessRule(new Path("**"),
				IAccessRule.K_NON_ACCESSIBLE);
		return result;
	}

	String[] parse(String s) {
		List<String> results = new ArrayList<String>();
		if (s != null) {
			boolean insideQuotes = false;
			int startIndex = 0;
			for (int i = 0; i < s.length(); i++) {
				if ('\"' == s.charAt(i)) {
					insideQuotes = !insideQuotes;
				} else if (',' == s.charAt(i)) {
					if (!insideQuotes) {
						results.add(s.substring(startIndex, i));
						startIndex = i + 1;
					}
				}
			}
			if (startIndex < s.length()) {
				results.add(s.substring(startIndex));
			}
		}

		return results.toArray(new String[results.size()]);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * org.eclipse.ui.IActionDelegate#selectionChanged(org.eclipse.jface.action
	 * .IAction, org.eclipse.jface.viewers.ISelection)
	 */
	public void selectionChanged(IAction action, ISelection selection) {
		this.selection = selection;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * org.eclipse.ui.IObjectActionDelegate#setActivePart(org.eclipse.jface.
	 * action.IAction, org.eclipse.ui.IWorkbenchPart)
	 */
	public void setActivePart(IAction action, IWorkbenchPart targetPart) {
	}
}
