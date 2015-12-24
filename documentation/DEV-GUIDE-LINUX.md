
# CloudUnit developement environment

You are reading the wright guide, if you want to setup an environment to contribute to CloudUnit development.
If you simply wish to test CloudUnit, you should maybe run our [Demo Vagrant box](DEMO-GUIDE.md).

## Requirements

* Linux Ubuntu/Debian
* Git
* Java 1.8
* VirtualBox 5.0.4+ (www.virtualbox.org) - install before Vagrant
* Vagrant 1.7.8+ (www.vagrantup.com)
* Maven 3+ (maven.apache.org)
* Ansible (see further for installation)

## Architecture sources

* `cloudunit/cu-manager`  : Maven project 
* `cloudunit/cu-plaform`  : Shell scripts for administration 
* `cloudunit/cu-images` : Docker images

## Dev Rules and Principles

* You have to configure a local dns (see further) to send any requests from your host to VM (IP fixed at 192.168.50.4) 
* A reverse proxy is included into vagrantbox to route the request (*.cloudunit.dev) to the right app.
* You use your favorite idea (intellij, Eclipse) to develop the maven project into 'cloudunit/cu-manager'.
* The backend is a spring application exposing a REST API
* The frontend is an AngularJS 1.x consuming the backend API
* You run the project with an embedded tomcat via maven tasks (tomcat:run). No need to install Tomcat locally.
* Mysql is included into vagrantbox so no need to install it locally.
* Ansible is needed locally to provision Vagrantbox (it will disapear in a near future...)

## Installation 

### Local DNS

CloudUnit uses Docker and Java but others components. As pre-requisites, you need to install them to have a complete dev stack. You need to install a local DNS for entry.
```
Dnsmasq is a lightweight, easy to configure DNS forwarder 
and DHCP server […] is targeted at home networks[.]
```
You need to add a local DNS entry pointing to the vagrant IP address. More precisely, any address ending with admin.cloudunit.io shoud point to `192.168.50.4`. On Ubuntu, a simple way to achieve this is to install dnsmasq:
```
sudo apt-get install dnsmasq
sudo vi /etc/dnsmasq.conf
# Add the line: address=/.cloudunit.dev/192.168.50.4                      
sudo service dnsmasq restart
```

### How to install Ansible 1.9+ if needed

```
sudo apt-get install software-properties-common
sudo apt-add-repository ppa:ansible/ansible
sudo apt-get update
sudo apt-get install ansible
```

### How to install Vagrant plugins
```
vagrant plugin install vagrant-reload
vagrant plugin install vagrant-vbguest
```
### Source code installation

Follow these instructions : 
```
sudo apt-get install nodejs npm ruby ruby-dev
sudo ln -s "$(which nodejs)" /usr/bin/node
mkdir $HOME/infrastructure
cd $HOME/infrastructure
git clone https://github.com/Treeptik/CU-infrastructure
cd $HOME && git clone https://github.com/Treeptik/CloudUnit.git
cd $HOME/CloudUnit/cu-manager/src/main/webapp && sudo npm install grunt -g
cd $HOME/CloudUnit/cu-manager/src/main/webapp && sudo npm install grunt-cli -g
cd $HOME/CloudUnit/cu-manager/src/main/webapp && sudo npm install bower -g
cd $HOME/CloudUnit/cu-manager/src/main/webapp && sudo npm install -g n
cd $HOME/CloudUnit/cu-manager/src/main/webapp && sudo stable n
cd $HOME/CloudUnit/cu-manager/src/main/webapp && bower install
cd $HOME/CloudUnit/cu-manager/src/main/webapp && sudo gem install compass
cd $HOME/CloudUnit/cu-manager/src/main/webapp && sudo npm install grunt
cd $HOME/CloudUnit/cu-manager/src/main/webapp && sudo npm install grunt-cli
cd $HOME/CloudUnit/cu-manager/src/main/webapp && sudo npm install

```

## How to start Environment Development

To run the UI for development (http://0.0.0.0:9000)
```
cd $HOME/CloudUnit/cu-manager/src/main/webapp && vagrant up dev
cd $HOME/CloudUnit/cu-manager/src/main/webapp && grunt serve
cd $HOME/CloudUnit/cu-manager
mvn clean compile tomcat7:run -DskipTests -Dspring.profiles.active=vagrant
```

# FAQ

All questions and answers about dev tasks

## How to reset Environment Development

```
vagrant ssh dev
cloudunit/cu-platform/reset-all.sh -y
```

## How to rebuild images

Update your sources, build the images and reninit the database :

```
vagrant ssh dev
cloudunit/cu-images/build-images.sh
cloudunit/cu-vagrant/utils/reset-all.sh -y
```

By default, docker cache is disabled. So all images will be built again.
To speed up, you can activate the cache but it could be dangerous 
if you modify a parent image with docker inheritance.

## How to resize properly a vagrant box for 60 Go disk size

```
vagrant box add debian/jessie64 --box-version 8.2.2
cd ~/.vagrant.d/boxes/debian-VAGRANTSLASH-jessie64/8.2.2/virtualbox/
VBoxManage clonehd debian-jessie-disk1.vmdk tmp-disk.vdi --format vdi
VBoxManage modifyhd tmp-disk.vdi --resize 61440
VBoxManage clonehd tmp-disk.vdi resized-disk.vmdk --format vmdk
rm tmp-disk.vdi debian-jessie-disk1.vmdk
mv resized-disk.vmdk debian-jessie-disk1.vmdk
``` 