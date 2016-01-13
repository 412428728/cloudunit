/*
 * LICENCE : CloudUnit is available under the GNU Affero General Public License : https://gnu.org/licenses/agpl.html
 * but CloudUnit is licensed too under a standard commercial license.
 * Please contact our sales team if you would like to discuss the specifics of our Enterprise license.
 * If you are not sure whether the AGPL is right for you,
 * you can always test our software under the AGPL and inspect the source code before you contact us
 * about purchasing a commercial license.
 *
 * LEGAL TERMS : "CloudUnit" is a registered trademark of Treeptik and can't be used to endorse
 * or promote products derived from this project without prior written permission from Treeptik.
 * Products or services derived from this software may not be called "CloudUnit"
 * nor may "Treeptik" or similar confusing terms appear in their names without prior written permission.
 * For any questions, contact us : contact@treeptik.fr
 */

package fr.treeptik.cloudunit.controller;

import fr.treeptik.cloudunit.dto.FileUnit;
import fr.treeptik.cloudunit.dto.HttpOk;
import fr.treeptik.cloudunit.dto.JsonResponse;
import fr.treeptik.cloudunit.exception.CheckException;
import fr.treeptik.cloudunit.exception.ServiceException;
import fr.treeptik.cloudunit.model.Application;
import fr.treeptik.cloudunit.model.Status;
import fr.treeptik.cloudunit.model.User;
import fr.treeptik.cloudunit.service.ApplicationService;
import fr.treeptik.cloudunit.service.FileService;
import fr.treeptik.cloudunit.utils.AuthentificationUtils;
import fr.treeptik.cloudunit.utils.FilesUtils;
import org.apache.commons.compress.utils.IOUtils;
import org.apache.commons.io.FileUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import javax.inject.Inject;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/*
 * Controller for resources (files + folders) into DockerContainer.
 *
 * TODO : ADD Status Application management. UseCase : disable user action durant long time download/upload
 *
 * Created by nicolas on 20/05/15.
 */

@Controller
@RequestMapping("/file")
public class FileController {

    private static final long serialVersionUID = 1L;

    private final transient Logger logger = LoggerFactory
        .getLogger(FileController.class);

    @Inject
    private FileService fileService;

    @Inject
    private ApplicationService applicationService;

    @Inject
    private AuthentificationUtils authentificationUtils;

    private Locale locale = Locale.ENGLISH;

    /**
     * @param containerId
     * @param path
     * @return
     * @throws ServiceException
     * @throws CheckException
     */
    @RequestMapping(value = "/container/{containerId}/path/{path}", method = RequestMethod.GET)
    public
    @ResponseBody
    List<FileUnit> listByContainerIdAndPath(
        @PathVariable String containerId, @PathVariable String path)
        throws ServiceException, CheckException {

        if (logger.isInfoEnabled()) {
            logger.info("containerId:" + containerId);
            logger.info("path:" + path);
        }

        path = convertPathFromUI(path);
        List<FileUnit> fichiers = fileService.listByContainerIdAndPath(
            containerId, path);

        return fichiers;
    }

    /**
     * Upload a file into a container
     *
     * @return
     * @throws IOException
     * @throws ServiceException
     * @throws CheckException
     */
    @RequestMapping(value = "/container/{containerId}/application/{applicationName}/path/{path}", method = RequestMethod.POST, consumes = {
        "multipart/form-data"})
    public
    @ResponseBody
    JsonResponse uploadFileToContainer(
        @RequestPart("file") MultipartFile fileUpload,
        @PathVariable final String containerId,
        @PathVariable final String applicationName,
        @PathVariable final String path, HttpServletRequest request,
        HttpServletResponse response)
        throws IOException, ServiceException,
        CheckException {

        if (logger.isDebugEnabled()) {
            logger.debug("-- CALL UPLOAD FILE TO CONTAINER FS --");
            logger.debug("applicationName = " + applicationName);
            logger.debug("containerId = " + containerId);
            logger.debug("pathFile = " + path);
        }

        User user = authentificationUtils.getAuthentificatedUser();
        Application application = applicationService.findByNameAndUser(user,
            applicationName);

        // We must be sure there is no running action before starting new one
        this.authentificationUtils.canStartNewAction(user, application, locale);

        // Application is now pending
        applicationService.setStatus(application, Status.PENDING);

        if (application != null) {
            File file = File.createTempFile("upload-",
                FilesUtils.setSuffix(fileUpload.getOriginalFilename()));
            fileUpload.transferTo(file);

            try {
                fileService.sendFileToContainer(applicationName, containerId,
                    file, fileUpload.getOriginalFilename(), path);

            } catch (ServiceException e) {
                logger.error("Error during file upload : " + file);
                logger.error("containerId : " + containerId);
                logger.error("applicationName : " + applicationName);

            } finally {
                // in all case, the error during file upload cannot be critical.
                // We prefer to set the application in started mode
                applicationService.setStatus(application, Status.START);
            }
        }

        return new HttpOk();

    }

    /**
     * Delete resources (files and folders) into a container for a path
     *
     * @param containerId
     * @param applicationName
     * @param path
     * @return
     * @throws ServiceException
     * @throws CheckException
     * @throws IOException
     */
    @RequestMapping(value = "/container/{containerId}/application/{applicationName}/path/{path:.*}",
        method = RequestMethod.DELETE)
    public
    @ResponseBody
    JsonResponse deleteResourcesIntoContainer(
        @PathVariable final String containerId,
        @PathVariable final String applicationName,
        @PathVariable String path)
        throws ServiceException, CheckException,
        IOException {

        if (logger.isDebugEnabled()) {
            logger.debug("containerId:" + containerId);
            logger.debug("applicationName:" + applicationName);
            logger.debug("path:" + path);
        }

        path = convertPathFromUI(path);
        User user = authentificationUtils.getAuthentificatedUser();
        Application application = applicationService.findByNameAndUser(user,
            applicationName);

        fileService
            .deleteFilesFromContainer(applicationName, containerId, path);

        return new HttpOk();
    }

    /**
     * Download a file into a container
     *
     * @param containerId
     * @param applicationName
     * @param path
     * @param fileName
     * @param request
     * @param response
     * @throws ServiceException
     * @throws CheckException
     * @throws IOException
     * @returnoriginalName
     */
    @RequestMapping(value = "/container/{containerId}/application/{applicationName}/path/{path}/fileName/{fileName:.*}",
        method = RequestMethod.GET)
    public void downloadFileFromContainer(
        @PathVariable final String containerId,
        @PathVariable final String applicationName,
        @PathVariable String path, @PathVariable final String fileName,
        HttpServletRequest request, HttpServletResponse response)
        throws ServiceException, CheckException, IOException {

        if (logger.isDebugEnabled()) {
            logger.debug("containerId:" + containerId);
            logger.debug("applicationName:" + applicationName);
            logger.debug("fileName:" + fileName);
        }

        User user = authentificationUtils.getAuthentificatedUser();
        Application application = applicationService.findByNameAndUser(user,
            applicationName);

        // We must be sure there is no running action before starting new one
        this.authentificationUtils.canStartNewAction(user, application, locale);

        File file = File.createTempFile("previousDownload", FilesUtils.setSuffix(fileName));

        path = convertPathFromUI(path);
        Optional<File> fileFromContainer =
            fileService.getFileFromContainer(applicationName, containerId, file, fileName, path);

        // File can be empty but we need to return one
        if (fileFromContainer.filter(x -> x.length() == 0).isPresent()) {
            file = File.createTempFile(fileName, "");
            // put an empty space for empty file
            FileUtils.write(file, " ");
        }

        InputStream inputStream = new FileInputStream(file); //load the file
        IOUtils.copy(inputStream, response.getOutputStream());
        response.flushBuffer();

    }

    private String convertPathFromUI(String path) {
        if (path != null) {
            path = path.replaceAll("____", "/");
            path = path.replaceAll("__", "/");
        }
        return path;
    }
}
