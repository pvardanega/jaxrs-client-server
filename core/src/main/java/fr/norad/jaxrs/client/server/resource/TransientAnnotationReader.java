/**
 *
 *     Copyright (C) norad.fr
 *
 *     Licensed under the Apache License, Version 2.0 (the "License");
 *     you may not use this file except in compliance with the License.
 *     You may obtain a copy of the License at
 *
 *             http://www.apache.org/licenses/LICENSE-2.0
 *
 *     Unless required by applicable law or agreed to in writing, software
 *     distributed under the License is distributed on an "AS IS" BASIS,
 *     WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *     See the License for the specific language governing permissions and
 *     limitations under the License.
 */
package fr.norad.jaxrs.client.server.resource;

import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;
import javax.xml.bind.annotation.XmlTransient;
import com.sun.xml.bind.v2.model.annotation.AbstractInlineAnnotationReaderImpl;
import com.sun.xml.bind.v2.model.annotation.Locatable;
import com.sun.xml.bind.v2.model.annotation.RuntimeAnnotationReader;
import com.sun.xml.bind.v2.model.annotation.RuntimeInlineAnnotationReader;

/**
 * <p>
 * Patched version of JAXB's RuntimeAnnotationReader that can annotate set of classes, fields, and methods as
 * XmlTransient. Note that "<tt>@XmlTransient</tt> is mutually exclusive with all other JAXB defined annotations.
 * </p>
 * 
 * <p>
 * Usage:
 * 
 * <pre>
 *     // initialize our custom reader
 *     TransientAnnotationReader reader = new TransientAnnotationReader();
 *     reader.addTransientField(Throwable.class.getDeclaredField("stackTrace"));
 *     reader.addTransientMethod(Throwable.class.getDeclaredMethod("getStackTrace"));
 * 
 *     // initialize JAXB context
 *     Map<String, Object> jaxbConfig = new HashMap<String, Object>();
 *     jaxbConfig.put(JAXBRIContext.ANNOTATION_READER, reader);
 *     JAXBContext ctx = JAXBContext.newInstance (PACKAGE_PATH, TransientAnnotationReader.class.getClassLoader(), jaxbConfig);
 * 
 *     // XMLlize something
 *     Marshaller m = ctx.create ();
 *     m.marshal (...);
 * </pre>
 * 
 * </p>
 * 
 * @see <a href="http://wiki.jboss.org/wiki/Wiki.jsp?page=JAXBIntroductions">JAXB Introductions</a>
 * 
 * @author Andy Malakov
 */
@SuppressWarnings("rawtypes")
public class TransientAnnotationReader extends AbstractInlineAnnotationReaderImpl<Type, Class, Field, Method>
        implements RuntimeAnnotationReader {

    private static class XmlTransientProxyHandler implements InvocationHandler {

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
            if (args == null || args.length == 0) {
                if (method.getName().equals("annotationType")) {
                    return XmlTransient.class;
                }
                if (method.getName().equals("toString")) {
                    return "@XmlTransient";
                }
            }
            throw new UnsupportedOperationException("@XmlTransient doesn't support method call: " + method.getName());
        }

        private static XmlTransient create() {
            return (XmlTransient) Proxy.newProxyInstance(XmlTransientProxyHandler.class.getClassLoader(),
                    new Class[] { XmlTransient.class }, new XmlTransientProxyHandler());
        }
    }

    private static final Annotation XML_TRANSIENT_ANNOTATION = XmlTransientProxyHandler.create();
    private static final Annotation[] XML_TRANSIENT_ANNOTATION_ONLY = { XML_TRANSIENT_ANNOTATION };

    private final RuntimeInlineAnnotationReader delegate = new RuntimeInlineAnnotationReader();
    private final List<Class<?>> transientClasses = new ArrayList<Class<?>>();
    private final List<Field> transientFields = new ArrayList<Field>();
    private final List<Method> transientMethods = new ArrayList<Method>();

    public TransientAnnotationReader() {
    }

    // API

    public void addTransientClass(Class<?> cls) {
        transientClasses.add(cls);
    }

    public void addTransientField(Field field) {
        transientFields.add(field);
    }

    public void addTransientMethod(Method method) {
        transientMethods.add(method);
    }

    /// Classes

    @Override
    public boolean hasClassAnnotation(Class clazz, Class<? extends Annotation> annotationType) {
        if (transientClasses.contains(clazz)) {
            return true;
        }
        return delegate.hasClassAnnotation(clazz, annotationType);
    }

    @Override
    @SuppressWarnings("unchecked")
    public <A extends Annotation> A getClassAnnotation(Class<A> annotationType, Class clazz, Locatable srcPos) {
        if (transientClasses.contains(clazz)) {
            return (A) XML_TRANSIENT_ANNOTATION;
        }

        //return LocatableAnnotation.create(((Class<?>) clazz).getAnnotation(annotationType), srcPos);
        return delegate.getClassAnnotation(annotationType, clazz, srcPos);
    }

    /// Fields

    @Override
    public boolean hasFieldAnnotation(Class<? extends Annotation> annotationType, Field field) {
        if (XmlTransient.class.isAssignableFrom(annotationType)) {
            if (transientFields.contains(field)) {
                return true;
            }
        }
        return delegate.hasFieldAnnotation(annotationType, field);
    }

    @Override
    @SuppressWarnings("unchecked")
    public <A extends Annotation> A getFieldAnnotation(Class<A> annotationType, Field field, Locatable srcPos) {
        if (XmlTransient.class.isAssignableFrom(annotationType)) {
            if (transientFields.contains(field)) {
                return (A) XML_TRANSIENT_ANNOTATION;
            }
        }
        return delegate.getFieldAnnotation(annotationType, field, srcPos);
    }

    @Override
    public Annotation[] getAllFieldAnnotations(Field field, Locatable srcPos) {
        if (transientFields.contains(field)) {
            return XML_TRANSIENT_ANNOTATION_ONLY;
        }

        return delegate.getAllFieldAnnotations(field, srcPos);
    }

    /// Methods

    @Override
    public boolean hasMethodAnnotation(Class<? extends Annotation> annotationType, Method method) {
        if (XmlTransient.class.isAssignableFrom(annotationType)) {
            if (transientMethods.contains(method)) {
                return true;
            }

        }
        return delegate.hasMethodAnnotation(annotationType, method);
    }

    @Override
    @SuppressWarnings("unchecked")
    public <A extends Annotation> A getMethodAnnotation(Class<A> annotationType, Method method, Locatable srcPos) {
        if (XmlTransient.class.isAssignableFrom(annotationType)) {
            if (transientMethods.contains(method)) {
                return (A) XML_TRANSIENT_ANNOTATION;
            }

        }
        return delegate.getMethodAnnotation(annotationType, method, srcPos);
    }

    @Override
    public Annotation[] getAllMethodAnnotations(Method method, Locatable srcPos) {
        if (transientMethods.contains(method)) {
            return XML_TRANSIENT_ANNOTATION_ONLY;
        }

        return delegate.getAllMethodAnnotations(method, srcPos);
    }

    // default

    @Override
    public <A extends Annotation> A getMethodParameterAnnotation(Class<A> annotation, Method method, int paramIndex,
            Locatable srcPos) {
        return delegate.getMethodParameterAnnotation(annotation, method, paramIndex, srcPos);
    }

    @Override
    public <A extends Annotation> A getPackageAnnotation(Class<A> a, Class clazz, Locatable srcPos) {
        return delegate.getPackageAnnotation(a, clazz, srcPos);
    }

    @Override
    public Class getClassValue(Annotation a, String name) {
        return delegate.getClassValue(a, name);
    }

    @Override
    public Class[] getClassArrayValue(Annotation a, String name) {
        return delegate.getClassArrayValue(a, name);
    }

    @Override
    protected String fullName(Method m) {
        // same as RuntimeInlineAnnotationReader.fullName()
        return m.getDeclaringClass().getName() + '#' + m.getName();
    }

}
