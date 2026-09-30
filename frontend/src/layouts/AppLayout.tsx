import { useMemo, useState } from 'react';
import { NavLink, Outlet, useLocation, useNavigate } from 'react-router-dom';
import AppBar from '@mui/material/AppBar';
import Avatar from '@mui/material/Avatar';
import Box from '@mui/material/Box';
import Chip from '@mui/material/Chip';
import Divider from '@mui/material/Divider';
import Drawer from '@mui/material/Drawer';
import IconButton from '@mui/material/IconButton';
import InputAdornment from '@mui/material/InputAdornment';
import List from '@mui/material/List';
import ListItemButton from '@mui/material/ListItemButton';
import ListItemIcon from '@mui/material/ListItemIcon';
import ListItemText from '@mui/material/ListItemText';
import Menu from '@mui/material/Menu';
import MenuItem from '@mui/material/MenuItem';
import TextField from '@mui/material/TextField';
import Toolbar from '@mui/material/Toolbar';
import Tooltip from '@mui/material/Tooltip';
import Typography from '@mui/material/Typography';
import useMediaQuery from '@mui/material/useMediaQuery';
import { useTheme } from '@mui/material/styles';
import AccessTimeOutlined from '@mui/icons-material/AccessTimeOutlined';
import DarkModeOutlined from '@mui/icons-material/DarkModeOutlined';
import LightModeOutlined from '@mui/icons-material/LightModeOutlined';
import LogoutOutlined from '@mui/icons-material/LogoutOutlined';
import MenuIcon from '@mui/icons-material/Menu';
import PersonOutline from '@mui/icons-material/PersonOutline';
import SearchOutlined from '@mui/icons-material/SearchOutlined';
import { useQuery } from '@tanstack/react-query';
import { organisationApi } from '@/api/organisation';
import { NotificationBell } from '@/features/notifications/NotificationBell';
import { BrandMark } from '@/components/BrandMark';
import { navigation } from '@/config/navigation';
import { useAuth } from '@/contexts/AuthContext';
import { useColorMode } from '@/contexts/ColorModeContext';
import { density } from '@/theme/tokens';

export function AppLayout() {
  const theme = useTheme();
  const navigate = useNavigate();
  const location = useLocation();
  const { user, signOut, canAny } = useAuth();
  const { mode, toggle } = useColorMode();
  const isDesktop = useMediaQuery(theme.breakpoints.up('lg'));
  const [mobileOpen, setMobileOpen] = useState(false);
  const [profileAnchor, setProfileAnchor] = useState<HTMLElement | null>(null);

  const { data: settings } = useQuery({
    queryKey: ['settings', 'company'],
    queryFn: organisationApi.settings,
    staleTime: 10 * 60 * 1000,
  });

  const sections = useMemo(
    () =>
      navigation
        .map((section) => ({
          ...section,
          items: section.items.filter((item) => canAny(item.permissions)),
        }))
        .filter((section) => section.items.length > 0),
    [canAny],
  );

  const shift = settings
    ? `${formatTime(settings.workStartTime)} – ${formatTime(settings.workEndTime)}`
    : null;

  const sidebar = (
    <Box sx={{ display: 'flex', flexDirection: 'column', height: '100%' }}>
      <Box sx={{ px: 5, py: 4, borderBottom: 1, borderColor: 'divider' }}>
        <BrandMark />
      </Box>

      <Box sx={{ flex: 1, overflowY: 'auto', px: 3, py: 4 }}>
        {sections.map((section) => (
          <Box key={section.heading} sx={{ mb: 4 }}>
            <Typography
              variant="overline"
              sx={{ px: 3, color: 'text.secondary', display: 'block', mb: 1.5 }}
            >
              {section.heading}
            </Typography>
            <List disablePadding sx={{ display: 'flex', flexDirection: 'column', gap: 0.5 }}>
              {section.items.map((item) => {
                const active = location.pathname.startsWith(item.path);
                return (
                  <ListItemButton
                    key={item.path}
                    component={item.comingSoon ? 'div' : NavLink}
                    to={item.comingSoon ? undefined : item.path}
                    onClick={() => !item.comingSoon && !isDesktop && setMobileOpen(false)}
                    disabled={item.comingSoon}
                    sx={{
                      px: 3,
                      py: 1.75,
                      color: active ? 'primary.contrastText' : 'text.secondary',
                      bgcolor: active ? 'primary.main' : 'transparent',
                      '&:hover': {
                        bgcolor: active ? 'primary.dark' : 'action.hover',
                        color: active ? 'primary.contrastText' : 'text.primary',
                      },
                      '&.Mui-disabled': { opacity: 0.45 },
                    }}
                  >
                    <ListItemIcon sx={{ minWidth: 30, color: 'inherit' }}>
                      <item.icon sx={{ fontSize: 18 }} />
                    </ListItemIcon>
                    <ListItemText
                      primary={item.label}
                      primaryTypographyProps={{ fontSize: 13.5, fontWeight: active ? 600 : 500 }}
                    />
                    {item.comingSoon && (
                      <Typography variant="caption" sx={{ fontSize: 10, color: 'text.disabled' }}>
                        soon
                      </Typography>
                    )}
                  </ListItemButton>
                );
              })}
            </List>
          </Box>
        ))}
      </Box>

      <Box sx={{ px: 5, py: 4, borderTop: 1, borderColor: 'divider' }}>
        <Typography variant="caption" sx={{ color: 'text.secondary', display: 'block' }}>
          Access scope
        </Typography>
        <Typography variant="subtitle2" sx={{ textTransform: 'capitalize' }}>
          {(user?.dataScope ?? '').toLowerCase() || '—'}
        </Typography>
      </Box>
    </Box>
  );

  return (
    <Box sx={{ display: 'flex', minHeight: '100vh', bgcolor: 'background.default' }}>
      <Box
        component="nav"
        sx={{ width: { lg: density.sidebarWidth }, flexShrink: { lg: 0 } }}
        aria-label="Main navigation"
      >
        <Drawer
          variant={isDesktop ? 'permanent' : 'temporary'}
          open={isDesktop ? true : mobileOpen}
          onClose={() => setMobileOpen(false)}
          ModalProps={{ keepMounted: true }}
          sx={{
            '& .MuiDrawer-paper': {
              width: density.sidebarWidth,
              boxSizing: 'border-box',
              borderRight: 1,
              borderColor: 'divider',
              bgcolor: 'background.paper',
            },
          }}
        >
          {sidebar}
        </Drawer>
      </Box>

      <Box sx={{ flex: 1, minWidth: 0, display: 'flex', flexDirection: 'column' }}>
        <AppBar
          position="sticky"
          color="inherit"
          sx={{
            bgcolor: 'background.paper',
            borderBottom: 1,
            borderColor: 'divider',
            boxShadow: 'none',
          }}
        >
          <Toolbar sx={{ minHeight: density.topBarHeight, gap: 3, px: { xs: 4, md: 6 } }}>
            {!isDesktop && (
              <IconButton edge="start" onClick={() => setMobileOpen(true)} aria-label="Open navigation">
                <MenuIcon />
              </IconButton>
            )}

            <TextField
              size="small"
              placeholder="Search employees, departments"
              onKeyDown={(event) => {
                if (event.key === 'Enter') {
                  const value = (event.target as HTMLInputElement).value.trim();
                  if (value) {
                    navigate(`/employees?query=${encodeURIComponent(value)}`);
                  }
                }
              }}
              InputProps={{
                startAdornment: (
                  <InputAdornment position="start">
                    <SearchOutlined sx={{ fontSize: 17, color: 'text.disabled' }} />
                  </InputAdornment>
                ),
              }}
              sx={{ maxWidth: 340, flex: 1, display: { xs: 'none', sm: 'block' } }}
            />

            {shift && (
              <Chip
                icon={<AccessTimeOutlined sx={{ fontSize: 14 }} />}
                label={`Shift ${shift}`}
                variant="outlined"
                sx={{ display: { xs: 'none', md: 'inline-flex' }, height: 26 }}
              />
            )}

            <Box sx={{ flex: 1 }} />

            <NotificationBell />

            <Tooltip title={mode === 'light' ? 'Switch to dark' : 'Switch to light'}>
              <IconButton onClick={toggle} aria-label="Toggle colour mode">
                {mode === 'light' ? (
                  <DarkModeOutlined sx={{ fontSize: 19 }} />
                ) : (
                  <LightModeOutlined sx={{ fontSize: 19 }} />
                )}
              </IconButton>
            </Tooltip>

            <Box
              onClick={(event) => setProfileAnchor(event.currentTarget)}
              role="button"
              tabIndex={0}
              onKeyDown={(event) => event.key === 'Enter' && setProfileAnchor(event.currentTarget)}
              sx={{
                display: 'flex',
                alignItems: 'center',
                gap: 2.5,
                cursor: 'pointer',
                px: 2,
                py: 1,
                borderRadius: 1.5,
                '&:hover': { bgcolor: 'action.hover' },
              }}
            >
              <Avatar
                src={user?.profileImageUrl ?? undefined}
                sx={{ width: 30, height: 30, fontSize: 12, bgcolor: 'primary.main' }}
              >
                {initials(user?.fullName)}
              </Avatar>
              <Box sx={{ display: { xs: 'none', md: 'block' }, lineHeight: 1.2 }}>
                <Typography sx={{ fontSize: 13, fontWeight: 600 }}>{user?.fullName}</Typography>
                <Typography variant="caption" sx={{ color: 'text.secondary' }}>
                  {user?.designation ?? user?.roles?.[0]?.replace(/_/g, ' ')}
                  {user?.employeeCode ? ` · ${user.employeeCode}` : ''}
                </Typography>
              </Box>
            </Box>

            <Menu
              anchorEl={profileAnchor}
              open={Boolean(profileAnchor)}
              onClose={() => setProfileAnchor(null)}
              anchorOrigin={{ vertical: 'bottom', horizontal: 'right' }}
              transformOrigin={{ vertical: 'top', horizontal: 'right' }}
            >
              <MenuItem
                onClick={() => {
                  setProfileAnchor(null);
                  navigate('/profile');
                }}
              >
                <ListItemIcon>
                  <PersonOutline sx={{ fontSize: 18 }} />
                </ListItemIcon>
                My profile
              </MenuItem>
              <Divider />
              <MenuItem
                onClick={async () => {
                  setProfileAnchor(null);
                  await signOut();
                  navigate('/login');
                }}
              >
                <ListItemIcon>
                  <LogoutOutlined sx={{ fontSize: 18 }} />
                </ListItemIcon>
                Sign out
              </MenuItem>
            </Menu>
          </Toolbar>
        </AppBar>

        <Box component="main" sx={{ flex: 1, px: { xs: 4, md: 6 }, py: 6, minWidth: 0 }}>
          <Outlet />
        </Box>
      </Box>
    </Box>
  );
}

function initials(name?: string | null): string {
  if (!name) {
    return '?';
  }
  return name
    .split(' ')
    .filter(Boolean)
    .slice(0, 2)
    .map((part) => part[0]?.toUpperCase())
    .join('');
}

function formatTime(value: string): string {
  const [hour, minute] = value.split(':');
  return `${hour}:${minute}`;
}
