%define __jar_repack %{nil}
%define debug_package %{nil}
%define __strip /bin/true
%define __os_install_post   /bin/true
%define __check_files /bin/true
Summary: donkey
Name: donkey
Version: 0.1.0
Release: 5
Epoch: 0
BuildArchitectures: noarch
Group: Applications
BuildRoot: %{_tmppath}/%{name}-%{version}-buildroot
License: BSD
Provides: donkey
Requires: iplant-service-config >= 0.1.0-4
Source0: %{name}-%{version}.tar.gz

%description
iPlant Donkey

%pre
getent group iplant > /dev/null || groupadd -r iplant
getent passwd iplant > /dev/null || useradd -r -g iplant -d /home/iplant -s /bin/bash -c "User for the iPlant services." iplant
exit 0

%prep
%setup -q
mkdir -p $RPM_BUILD_ROOT/etc/init.d/

%build
unset JAVA_OPTS
lein deps
lein uberjar

%install
install -d $RPM_BUILD_ROOT/usr/local/lib/donkey/
install -d $RPM_BUILD_ROOT/var/run/donkey/
install -d $RPM_BUILD_ROOT/var/lock/subsys/donkey/
install -d $RPM_BUILD_ROOT/var/log/donkey/
install -d $RPM_BUILD_ROOT/etc/donkey/

install donkey $RPM_BUILD_ROOT/etc/init.d/
install donkey-1.0.0-SNAPSHOT-standalone.jar $RPM_BUILD_ROOT/usr/local/lib/donkey/
install conf/log4j.properties $RPM_BUILD_ROOT/etc/donkey/

%post
/sbin/chkconfig --add donkey

%preun
if [ $1 -eq 0 ] ; then
	/sbin/service donkey stop >/dev/null 2>&1
	/sbin/chkconfig --del donkey
fi

%postun
if [ "$1" -ge "1" ] ; then
	/sbin/service donkey condrestart >/dev/null 2>&1 || :
fi

%clean
lein clean
rm -r lib/*
rm -r $RPM_BUILD_ROOT

%files
%attr(-,iplant,iplant) /usr/local/lib/donkey/
%attr(-,iplant,iplant) /var/run/donkey/
%attr(-,iplant,iplant) /var/lock/subsys/donkey/
%attr(-,iplant,iplant) /var/log/donkey/
%attr(-,iplant,iplant) /etc/donkey/

%config %attr(0644,iplant,iplant) /etc/donkey/log4j.properties
%config %attr(0644,iplant,iplant) /etc/donkey/donkey.properties

%attr(0755,root,root) /etc/init.d/donkey
%attr(0644,iplant,iplant) /usr/local/lib/donkey/donkey-1.0.0-SNAPSHOT-standalone.jar
